package com.infolink.collector.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * JSON 응답 파서
 * - 전체 JSON → 트리 구조 변환 (테스트 모드)
 * - data_root_path 기준 → List<Map> 추출 (실행 모드)
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ResponseParser {

    private final ObjectMapper objectMapper;

    /**
     * 테스트 모드: JSON 응답을 트리 구조로 변환
     */
    public TreeNode parseToTree(String jsonBody) throws Exception {
        JsonNode root = objectMapper.readTree(jsonBody);
        return buildTree("root", root);
    }

    /**
     * 실행 모드: data_root_path 기준으로 레코드 배열 추출
     */
    public List<Map<String, Object>> extractRecords(String jsonBody, String dataRootPath) throws Exception {
        JsonNode root = objectMapper.readTree(jsonBody);
        JsonNode target = navigateTo(root, dataRootPath);

        if (target == null || !target.isArray()) {
            log.warn("data_root_path '{}' 에서 배열을 찾을 수 없음", dataRootPath);
            return Collections.emptyList();
        }

        List<Map<String, Object>> records = new ArrayList<>();
        for (JsonNode item : target) {
            Map<String, Object> record = flattenNode("", item);
            records.add(record);
        }
        return records;
    }

    /**
     * data_root_path로부터 필드 목록 추출 (매핑 후보)
     */
    public List<FieldInfo> extractFields(String jsonBody, String dataRootPath) throws Exception {
        JsonNode root = objectMapper.readTree(jsonBody);
        JsonNode target = navigateTo(root, dataRootPath);

        if (target == null || !target.isArray() || target.isEmpty()) {
            return Collections.emptyList();
        }

        // 첫 번째 요소에서 필드 추출
        JsonNode firstItem = target.get(0);
        List<FieldInfo> fields = new ArrayList<>();
        extractFieldsFromNode("", firstItem, fields);
        return fields;
    }

    // --- 내부 메서드 ---

    private TreeNode buildTree(String name, JsonNode node) {
        TreeNode treeNode = new TreeNode();
        treeNode.name = name;

        if (node.isObject()) {
            treeNode.type = "object";
            treeNode.children = new ArrayList<>();
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                treeNode.children.add(buildTree(field.getKey(), field.getValue()));
            }
        } else if (node.isArray()) {
            treeNode.type = "array";
            treeNode.arraySize = node.size();
            // 배열 내 첫 번째 요소를 샘플로 표시
            if (!node.isEmpty()) {
                JsonNode first = node.get(0);
                if (first.isObject()) {
                    treeNode.children = new ArrayList<>();
                    Iterator<Map.Entry<String, JsonNode>> fields = first.fields();
                    while (fields.hasNext()) {
                        Map.Entry<String, JsonNode> field = fields.next();
                        treeNode.children.add(buildTree(field.getKey(), field.getValue()));
                    }
                }
            }
        } else {
            // 리프 노드 (string, number, boolean, null)
            treeNode.type = inferType(node);
            treeNode.sampleValue = node.isNull() ? null : node.asText();
        }

        return treeNode;
    }

    private JsonNode navigateTo(JsonNode root, String path) {
        if (path == null || path.isBlank() || "$".equals(path.trim())) return root;

        String cleanPath = path.startsWith("$.") ? path.substring(2) : path;
        String[] parts = cleanPath.split("\\.");
        JsonNode current = root;
        for (String part : parts) {
            if (current == null) return null;
            current = current.get(part);
        }
        return current;
    }

    private Map<String, Object> flattenNode(String prefix, JsonNode node) {
        Map<String, Object> result = new LinkedHashMap<>();

        if (node.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                String key = prefix.isEmpty() ? field.getKey() : prefix + "." + field.getKey();
                if (field.getValue().isObject()) {
                    result.putAll(flattenNode(key, field.getValue()));
                } else {
                    result.put(key, nodeToValue(field.getValue()));
                }
            }
        }

        return result;
    }

    private void extractFieldsFromNode(String prefix, JsonNode node, List<FieldInfo> fields) {
        if (node.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> entries = node.fields();
            while (entries.hasNext()) {
                Map.Entry<String, JsonNode> entry = entries.next();
                String path = prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey();
                JsonNode value = entry.getValue();

                if (value.isObject()) {
                    extractFieldsFromNode(path, value, fields);
                } else if (!value.isArray()) {
                    FieldInfo info = new FieldInfo();
                    info.fieldPath = path;
                    info.fieldType = inferType(value);
                    info.sampleValue = value.isNull() ? null : value.asText();
                    fields.add(info);
                }
            }
        }
    }

    private String inferType(JsonNode node) {
        if (node.isTextual()) return "string";
        if (node.isInt() || node.isLong()) return "integer";
        if (node.isFloat() || node.isDouble() || node.isBigDecimal()) return "number";
        if (node.isBoolean()) return "boolean";
        if (node.isNull()) return "null";
        return "unknown";
    }

    private Object nodeToValue(JsonNode node) {
        if (node.isNull()) return null;
        if (node.isTextual()) return node.asText();
        if (node.isInt()) return node.asInt();
        if (node.isLong()) return node.asLong();
        if (node.isFloat() || node.isDouble()) return node.asDouble();
        if (node.isBoolean()) return node.asBoolean();
        return node.asText();
    }

    // --- DTO ---

    public static class TreeNode {
        public String name;
        public String type;           // "object", "array", "string", "integer", "number", "boolean", "null"
        public String sampleValue;    // 리프 노드의 샘플값
        public Integer arraySize;     // array일 때 원소 수
        public List<TreeNode> children;

        // JSON 직렬화용 getter
        public String getName() { return name; }
        public String getType() { return type; }
        public String getSampleValue() { return sampleValue; }
        public Integer getArraySize() { return arraySize; }
        public List<TreeNode> getChildren() { return children; }
    }

    public static class FieldInfo {
        public String fieldPath;
        public String fieldType;
        public String sampleValue;

        public String getFieldPath() { return fieldPath; }
        public String getFieldType() { return fieldType; }
        public String getSampleValue() { return sampleValue; }
    }
}
