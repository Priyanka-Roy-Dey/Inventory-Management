package com.inventory.api.dashboard;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Any signed-in role may read the dashboard.
 */
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

	private final DashboardService dashboard;

	public DashboardController(DashboardService dashboard) {
		this.dashboard = dashboard;
	}

	@GetMapping
	public DashboardResponse summary() {
		return dashboard.summary();
	}

}
