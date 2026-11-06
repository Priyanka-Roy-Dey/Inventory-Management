package com.inventory.api.alert;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Any signed-in role may read alerts. Newest first; every parameter is optional.
 */
@RestController
@RequestMapping("/api/alerts")
public class AlertController {

	private final AlertService alerts;

	public AlertController(AlertService alerts) {
		this.alerts = alerts;
	}

	@GetMapping
	public AlertPage list(@RequestParam(required = false) String search, @RequestParam(required = false) String cause,
			@RequestParam(required = false) Boolean stillLow, @RequestParam(required = false) Integer page,
			@RequestParam(required = false) Integer size) {
		return alerts.search(search, cause, stillLow, page, size);
	}

}
