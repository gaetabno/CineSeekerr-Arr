package com.cineseekerr.bot.model;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A manual-search release returned by an Arr app.
 *
 * Unknown API fields are preserved verbatim: Arr requires the complete release
 * resource when a user asks it to grab a result, rather than only its GUID.
 */
@JsonIgnoreProperties(ignoreUnknown = false)
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY,
        getterVisibility = JsonAutoDetect.Visibility.NONE,
        isGetterVisibility = JsonAutoDetect.Visibility.NONE)
public class ArrRelease {
    private String guid;
    private Integer indexerId;
    private String title;
    private Long size;
    private Integer seeders;
    private String indexer;
    private Boolean rejected;
    private List<String> rejections;
    private final Map<String, Object> additionalProperties = new LinkedHashMap<>();

    public ArrRelease() { }

    public ArrRelease(String guid, Integer indexerId, String title, Long size, Integer seeders,
                      String indexer, Boolean rejected, List<String> rejections) {
        this.guid = guid;
        this.indexerId = indexerId;
        this.title = title;
        this.size = size;
        this.seeders = seeders;
        this.indexer = indexer;
        this.rejected = rejected;
        this.rejections = rejections;
    }

    public String guid() { return guid; }
    public Integer indexerId() { return indexerId; }
    public String title() { return title; }
    public Long size() { return size; }
    public Integer seeders() { return seeders; }
    public String indexer() { return indexer; }
    public Boolean rejected() { return rejected; }
    public List<String> rejections() { return rejections; }

    public void setGuid(String guid) { this.guid = guid; }
    public void setIndexerId(Integer indexerId) { this.indexerId = indexerId; }
    public void setTitle(String title) { this.title = title; }
    public void setSize(Long size) { this.size = size; }
    public void setSeeders(Integer seeders) { this.seeders = seeders; }
    public void setIndexer(String indexer) { this.indexer = indexer; }
    public void setRejected(Boolean rejected) { this.rejected = rejected; }
    public void setRejections(List<String> rejections) { this.rejections = rejections; }

    @JsonAnySetter
    public void putAdditionalProperty(String name, Object value) { additionalProperties.put(name, value); }

    @JsonAnyGetter
    public Map<String, Object> additionalProperties() { return additionalProperties; }

    public boolean isApproved() { return !Boolean.TRUE.equals(rejected) && (rejections == null || rejections.isEmpty()); }
    public long sizeOrZero() { return size == null ? 0 : size; }
    public int seedersOrZero() { return seeders == null ? 0 : seeders; }
}
