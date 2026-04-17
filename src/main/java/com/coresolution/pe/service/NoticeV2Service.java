package com.coresolution.pe.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.springframework.stereotype.Service;

import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import com.coresolution.pe.entity.NoticeV2;
import com.coresolution.pe.mapper.NoticeV2Mapper;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class NoticeV2Service {
    private final NoticeV2Mapper mapper;
    private static final DateTimeFormatter F = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");

    public List<NoticeV2> getActiveNotices(int evalYear) {
        return mapper.findActive(evalYear);
    }

    public List<NoticeV2> list() {
        return mapper.findAll();
    }

    public NoticeV2 get(int id) {
        return mapper.findById(id);
    }

    public NoticeV2 create(NoticeV2Req req) {
        NoticeV2 n = toEntity(req);
        mapper.insert(n);
        return mapper.findById(n.getId());
    }

    public NoticeV2 update(int id, NoticeV2Req req) {
        NoticeV2 n = toEntity(req);
        n.setId(id);
        mapper.update(n);
        return mapper.findById(id);
    }

    public void delete(int id) {
        mapper.delete(id);
    }

    private NoticeV2 toEntity(NoticeV2Req r) {
        NoticeV2 n = new NoticeV2();
        n.setEvalYear(nullSafe(r.getEvalYear(), 0));
        n.setTitle(r.getTitle().trim());

        // (선택) XSS 최소 방어 – 허용 태그만 통과
        String safe = Jsoup.clean(r.getBodyMd(), Safelist.relaxed()
                .addTags("ul", "li", "span", "em")
                .addAttributes(":all", "class", "style"));
        n.setBodyMd(safe);

        n.setPinned(Boolean.TRUE.equals(r.getPinned()));
        n.setSortOrder(nullSafe(r.getSortOrder(), 10));
        n.setIsActive(Boolean.TRUE.equals(r.getIsActive()));
        n.setVersionTag(r.getVersionTag());

        n.setPublishFrom(parseDT(r.getPublishFrom()));
        n.setPublishTo(parseDT(r.getPublishTo()));
        return n;
    }

    private LocalDateTime parseDT(String s) {
        if (s == null || s.isBlank())
            return null;
        return LocalDateTime.parse(s.trim(), F);
    }

    private Integer nullSafe(Integer v, Integer def) {
        return v == null ? def : v;
    }

    @Data
    public static class NoticeV2Req {
        private Integer id;

        @Min(0)
        private Integer evalYear = 0;

        @NotBlank
        @Size(max = 100)
        private String title;

        @NotBlank
        private String bodyMd;

        private Boolean pinned = true;

        @NotNull
        private Integer sortOrder = 10;

        private String publishFrom; // "yyyy-MM-dd'T'HH:mm" or null
        private String publishTo;
        private Boolean isActive = true;

        @Size(max = 50)
        private String versionTag;
    }
}
