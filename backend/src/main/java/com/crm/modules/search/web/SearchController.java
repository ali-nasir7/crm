package com.crm.modules.search.web;

import com.crm.modules.search.service.SearchService;
import com.crm.security.CurrentUser;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/search")
@RequiredArgsConstructor
@Tag(name = "Global Search")
public class SearchController {

    private final SearchService searchService;

    @GetMapping
    public List<SearchService.SearchResultGroup> search(@RequestParam String q) {
        if (q == null || q.trim().length() < 2) return List.of();
        var principal = CurrentUser.require();
        return searchService.search(principal.getOrganizationId(), principal, q);
    }
}
