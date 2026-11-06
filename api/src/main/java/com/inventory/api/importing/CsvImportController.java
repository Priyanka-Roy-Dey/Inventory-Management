package com.inventory.api.importing;

import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Staff or Admin (see {@code SecurityConfig}). Upload the CSV as the multipart field {@code file}.
 */
@RestController
public class CsvImportController {

	private final CsvImportService imports;

	public CsvImportController(CsvImportService imports) {
		this.imports = imports;
	}

	@PostMapping(path = "/api/items/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ImportReport importItems(@RequestParam("file") MultipartFile file, @AuthenticationPrincipal Jwt jwt) {
		return imports.importFile(file, jwt.getSubject());
	}

}
