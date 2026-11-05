package com.inventory.api.item;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.inventory.api.TestcontainersConfiguration;
import com.inventory.api.auth.TokenService;
import com.inventory.api.stock.StockMovementRepository;
import com.inventory.api.user.Role;
import com.inventory.api.user.User;
import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class ItemImageTests {

	private static final byte[] PNG = { (byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10 };

	private final HttpClient http = HttpClient.newHttpClient();

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private TokenService tokens;

	@Autowired
	private ItemRepository items;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private StockMovementRepository movements;

	@Autowired
	private S3Client s3;

	@Value("${inventory.images.bucket}")
	private String bucket;

	private Item paper;

	@BeforeEach
	void createItem() {
		paper = items.saveAndFlush(new Item("PAP-A4", "A4 paper", "Stationery", 40, 10, new BigDecimal("4.50")));
	}

	@AfterEach
	void deleteItems() {
		movements.deleteAll();
		items.deleteAll();
	}

	private String bearerFor(Role role) {
		return "Bearer " + tokens.issue(new User(role.name().toLowerCase(), "hash", role));
	}

	private ResultActions requestUpload(Long itemId, String contentType, long size, Role as) throws Exception {
		return mockMvc.perform(post("/api/items/{id}/image/upload-url", itemId)
			.header(HttpHeaders.AUTHORIZATION, bearerFor(as))
			.contentType(MediaType.APPLICATION_JSON)
			.content("{ \"contentType\": \"%s\", \"size\": %d }".formatted(contentType, size)));
	}

	private ResultActions stockIn(int quantity, String imageKey, Role as) throws Exception {
		return mockMvc.perform(post("/api/items/{id}/movements", paper.getId())
			.header(HttpHeaders.AUTHORIZATION, bearerFor(as))
			.contentType(MediaType.APPLICATION_JSON)
			.content("{ \"type\": \"IN\", \"quantity\": %d, \"imageKey\": \"%s\" }".formatted(quantity, imageKey)));
	}

	private int quantityNow() {
		return items.findById(paper.getId()).orElseThrow().getQuantity();
	}

	/**
	 * Does what the browser does: asks for an upload URL, then sends the bytes straight to storage.
	 */
	private String upload(byte[] bytes, long declaredSize) throws Exception {
		String response = requestUpload(paper.getId(), "image/png", declaredSize, Role.STAFF).andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
		HttpResponse<Void> put = http.send(HttpRequest.newBuilder(URI.create(JsonPath.read(response, "$.uploadUrl")))
			.header("Content-Type", "image/png")
			.PUT(HttpRequest.BodyPublishers.ofByteArray(bytes))
			.build(), HttpResponse.BodyHandlers.discarding());
		assertThat(put.statusCode()).isEqualTo(200);
		return JsonPath.read(response, "$.key");
	}

	private boolean stored(String key) {
		try {
			s3.headObject(b -> b.bucket(bucket).key(key));
			return true;
		}
		catch (NoSuchKeyException ex) {
			return false;
		}
	}

	private String storedKey() {
		return jdbc.queryForObject("SELECT image_key FROM items WHERE id = ?", String.class, paper.getId());
	}

	@Test
	void anItemHasNoImageAtFirst() throws Exception {
		mockMvc.perform(get("/api/items/{id}", paper.getId()).header(HttpHeaders.AUTHORIZATION, bearerFor(Role.VIEWER)))
			.andExpect(jsonPath("$.imageUrl").doesNotExist());
	}

	@Test
	void givesAnUploadUrlForTheItem() throws Exception {
		requestUpload(paper.getId(), "image/png", PNG.length, Role.STAFF).andExpect(status().isOk())
			.andExpect(jsonPath("$.uploadUrl").isNotEmpty())
			.andExpect(jsonPath("$.key").value(org.hamcrest.Matchers.matchesPattern("items/" + paper.getId() + "/.+\\.png")))
			.andExpect(jsonPath("$.contentType").value("image/png"))
			.andExpect(jsonPath("$.expiresAt").isNotEmpty());
	}

	@Test
	void stockInWithAnImageAttachesItToTheItem() throws Exception {
		String key = upload(PNG, PNG.length);

		String response = stockIn(20, key, Role.STAFF).andExpect(status().isCreated())
			.andExpect(jsonPath("$.item.quantity").value(60))
			.andExpect(jsonPath("$.item.imageUrl").isNotEmpty())
			// The quantity change and the image each raise the version; the response must match the database.
			.andExpect(jsonPath("$.item.version").value(2))
			.andReturn()
			.getResponse()
			.getContentAsString();

		assertThat(storedKey()).isEqualTo(key);
		assertThat(items.findById(paper.getId()).orElseThrow().getVersion()).isEqualTo(2);
		HttpResponse<byte[]> image = http.send(
				HttpRequest.newBuilder(URI.create(JsonPath.read(response, "$.item.imageUrl"))).GET().build(),
				HttpResponse.BodyHandlers.ofByteArray());
		assertThat(image.statusCode()).isEqualTo(200);
		assertThat(image.body()).isEqualTo(PNG);
		mockMvc.perform(get("/api/items").header(HttpHeaders.AUTHORIZATION, bearerFor(Role.VIEWER)))
			.andExpect(jsonPath("$[0].imageUrl").isNotEmpty());
	}

	@Test
	void aNewerStockInImageReplacesTheOldOne() throws Exception {
		String first = upload(PNG, PNG.length);
		stockIn(1, first, Role.STAFF).andExpect(status().isCreated());

		String second = upload(PNG, PNG.length);
		stockIn(1, second, Role.STAFF).andExpect(status().isCreated());

		assertThat(storedKey()).isEqualTo(second);
		assertThat(stored(first)).isFalse();
		assertThat(stored(second)).isTrue();
	}

	@Test
	void stockInWithoutAnImageKeepsTheCurrentOne() throws Exception {
		String key = upload(PNG, PNG.length);
		stockIn(1, key, Role.STAFF).andExpect(status().isCreated());

		mockMvc
			.perform(post("/api/items/{id}/movements", paper.getId()).header(HttpHeaders.AUTHORIZATION, bearerFor(Role.STAFF))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{ \"type\": \"IN\", \"quantity\": 1 }"))
			.andExpect(status().isCreated());

		assertThat(storedKey()).isEqualTo(key);
	}

	@Test
	void onlyStockInCanCarryAnImage() throws Exception {
		String key = upload(PNG, PNG.length);

		mockMvc
			.perform(post("/api/items/{id}/movements", paper.getId()).header(HttpHeaders.AUTHORIZATION, bearerFor(Role.STAFF))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{ \"type\": \"OUT\", \"quantity\": 1, \"imageKey\": \"%s\" }".formatted(key)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("imageKey: is only allowed for IN"));

		assertThat(quantityNow()).isEqualTo(40);
	}

	@Test
	void acceptsOnlyJpegPngAndWebp() throws Exception {
		requestUpload(paper.getId(), "image/gif", 100, Role.STAFF).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("contentType: must be image/jpeg, image/png or image/webp"));
		requestUpload(paper.getId(), "image/jpeg", 100, Role.STAFF).andExpect(status().isOk());
		requestUpload(paper.getId(), "image/webp", 100, Role.STAFF).andExpect(status().isOk());
	}

	@Test
	void acceptsUpToTwoMegabytes() throws Exception {
		requestUpload(paper.getId(), "image/png", 2 * 1024 * 1024 + 1, Role.STAFF).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("size: must be at most 2 MB"));
		requestUpload(paper.getId(), "image/png", 0, Role.STAFF).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("size: must be greater than 0"));
		requestUpload(paper.getId(), "image/png", 2 * 1024 * 1024, Role.STAFF).andExpect(status().isOk());
	}

	@Test
	void rejectsAnUploadThatIsLargerThanAllowedAndKeepsTheStock() throws Exception {
		String key = upload(new byte[2 * 1024 * 1024 + 1], PNG.length);

		stockIn(5, key, Role.STAFF).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("Image is larger than 2 MB"));

		assertThat(stored(key)).isFalse();
		assertThat(storedKey()).isNull();
		assertThat(quantityNow()).isEqualTo(40);
	}

	@Test
	void rejectsAnImageThatWasNeverUploaded() throws Exception {
		stockIn(5, "items/" + paper.getId() + "/never-uploaded.png", Role.STAFF).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("Upload not found. Upload the image first."));

		assertThat(quantityNow()).isEqualTo(40);
	}

	@Test
	void rejectsAnImageOfAnotherItem() throws Exception {
		stockIn(5, "items/999999/someone-elses.png", Role.STAFF).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("Image does not belong to this item"));
	}

	@Test
	void viewerCannotUploadImages() throws Exception {
		requestUpload(paper.getId(), "image/png", 10, Role.VIEWER).andExpect(status().isForbidden());
	}

	@Test
	void anUnknownOrDeletedItemIsNotFound() throws Exception {
		requestUpload(999_999L, "image/png", 10, Role.STAFF).andExpect(status().isNotFound());

		jdbc.update("UPDATE items SET deleted_at = now() WHERE id = ?", paper.getId());
		requestUpload(paper.getId(), "image/png", 10, Role.STAFF).andExpect(status().isNotFound());
	}

}
