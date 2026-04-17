package com.coresolution.pe.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.coresolution.pe.entity.OrgMemberProgressRow;
import com.coresolution.pe.entity.OrgProgressRow;
import com.coresolution.pe.entity.PendingPairRow;
import com.coresolution.pe.mapper.AdminProgressByOrgMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AdminProgressByOrgService {

    private final AdminProgressByOrgMapper mapper;

    public Map<String, Object> getByOrg(int year, String ev, String search, String sort, int page, int size) {
        int offset = Math.max(0, (page - 1) * size);
        String usersTable = "users_" + year;

        List<OrgProgressRow> rows = mapper.selectOrgProgressPairs(year, ev, search, sort, size, offset, usersTable);

        // 임시: rows.size()로 대체
        int total = rows.size();

        int totalPairs = rows.stream().mapToInt(OrgProgressRow::getTotalPairs).sum();
        int completedPairs = rows.stream().mapToInt(OrgProgressRow::getCompletedPairs).sum();
        double progress = totalPairs > 0 ? Math.round((completedPairs * 1000.0 / totalPairs)) / 10.0 : 0.0;

        Map<String, Object> res = new HashMap<>();
        res.put("meta", Map.of("page", page, "size", size, "total", total));
        res.put("filters", Map.of("year", year, "ev", ev == null ? "ALL" : ev, "search", search == null ? "" : search,
                "sort", sort));
        res.put("rows", rows);
        res.put("summary", Map.of("totalPairs", totalPairs, "completedPairs", completedPairs, "progress", progress));
        return res;
    }

    public Map<String, Object> list(int year, String org, String ev, String search, String sort) {
        String usersTable = "personnel_evaluation.users_" + year;

        var rows = mapper.selectOrgMembers(year, org, ev, search, sort, usersTable);

        int needSum = rows.stream().mapToInt(OrgMemberProgressRow::getNeedPairs).sum();
        int doneSum = rows.stream().mapToInt(OrgMemberProgressRow::getDonePairs).sum();
        double progress = needSum > 0 ? Math.round(doneSum * 1000.0 / needSum) / 10.0 : 0.0;

        return Map.of(
                "filters",
                Map.of("year", year, "org", org, "ev", ev == null ? "ALL" : ev, "search", search == null ? "" : search,
                        "sort", sort),
                "rows", rows,
                "summary", Map.of("needPairs", needSum, "donePairs", doneSum, "progress", progress));
    }

    public List<PendingPairRow> pendingPairs(int year, String targetId, String ev) {
        return mapper.selectPendingPairs(year, targetId, ev);
    }
}