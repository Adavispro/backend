package com.adavis.mdm.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.util.Date;

@Slf4j
@Service
@RequiredArgsConstructor
public class BusinessIdGeneratorService {

    private final MongoTemplate mongoTemplate;

    public synchronized String nextId(String collectionName, String fieldName, String prefix, int width) {
        String sequenceName = resolveSequenceName(fieldName);

        // 1. Scan current max numeric suffix in collection to ensure we never collide with pre-existing or seeded records
        int maxExisting = getMaxNumericSuffix(collectionName, fieldName, prefix);

        // 2. Perform atomic findAndModify on id_sequences
        Query query = new Query(Criteria.where("sequenceName").is(sequenceName));
        Update update = new Update()
                .inc("currentValue", 1)
                .set("collectionName", collectionName)
                .set("fieldName", fieldName)
                .set("prefix", prefix)
                .set("padLength", width)
                .set("updatedAt", new Date())
                .setOnInsert("createdAt", new Date());

        FindAndModifyOptions options = FindAndModifyOptions.options().returnNew(true).upsert(true);
        Document seqDoc = mongoTemplate.findAndModify(query, update, options, Document.class, "id_sequences");

        int nextVal = 1;
        if (seqDoc != null && seqDoc.get("currentValue") != null) {
            Object rawVal = seqDoc.get("currentValue");
            if (rawVal instanceof Number number) {
                nextVal = number.intValue();
            }
        }

        // If the sequence value is behind the highest existing record, fast-forward to maxExisting + 1
        if (nextVal <= maxExisting) {
            nextVal = maxExisting + 1;
            Query syncQuery = new Query(Criteria.where("sequenceName").is(sequenceName));
            Update syncUpdate = new Update().set("currentValue", nextVal).set("updatedAt", new Date());
            mongoTemplate.updateFirst(syncQuery, syncUpdate, "id_sequences");
        }

        return prefix + String.format("%0" + width + "d", nextVal);
    }

    private String resolveSequenceName(String fieldName) {
        return fieldName;
    }

    private int getMaxNumericSuffix(String collectionName, String fieldName, String prefix) {
        int max = 0;
        try {
            for (Document document : mongoTemplate.findAll(Document.class, collectionName)) {
                Object rawValue = document.get(fieldName);
                if (rawValue == null) continue;
                String val = String.valueOf(rawValue);
                int suffix = extractNumericSuffix(val, prefix);
                if (suffix > max) {
                    max = suffix;
                }
            }
        } catch (Exception ex) {
            log.warn("Failed to scan max suffix for collection={}, field={}: {}", collectionName, fieldName, ex.getMessage());
        }
        return max;
    }

    private int extractNumericSuffix(String value, String prefix) {
        if (value == null || !value.startsWith(prefix)) {
            return -1;
        }

        String suffix = value.substring(prefix.length());
        if (!suffix.matches("\\d+")) {
            return -1;
        }

        try {
            return Integer.parseInt(suffix);
        } catch (NumberFormatException ex) {
            return -1;
        }
    }
}