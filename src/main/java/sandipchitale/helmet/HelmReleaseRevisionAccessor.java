package sandipchitale.helmet;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import io.fabric8.kubernetes.api.model.Secret;
import org.apache.commons.io.IOUtils;
import org.yaml.snakeyaml.external.biz.base64Coder.Base64Coder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;

public class HelmReleaseRevisionAccessor {
    private final NamespaceSecretReleaseRevision namespaceSecretReleaseRevision;

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private JsonNode releaseJsonNode;
    private String chartInfo ;
    private String values ;
    private String templates ;
    private String manifests ;
    private String hooks ;
    private String notes ;

    public HelmReleaseRevisionAccessor(NamespaceSecretReleaseRevision namespaceSecretReleaseRevision) {
        this.namespaceSecretReleaseRevision = namespaceSecretReleaseRevision;
    }

    public NamespaceSecretReleaseRevision getNamespaceSecretReleaseRevision() {
        return namespaceSecretReleaseRevision;
    }

    public JsonNode getReleaseJsonNode() {
        if (releaseJsonNode == null) {
            Secret secret = namespaceSecretReleaseRevision.secret();
            String release = secret.getData().get("release");
            byte[] decodedRelease = Base64Coder.decode(release);

            decodedRelease = Base64Coder.decode(new String(decodedRelease, StandardCharsets.UTF_8));
            try {
                GZIPInputStream gzipInputStream = new GZIPInputStream(new ByteArrayInputStream(decodedRelease));
                ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                IOUtils.copy(gzipInputStream, byteArrayOutputStream);
                String releaseJsonString = byteArrayOutputStream.toString(StandardCharsets.UTF_8);

                releaseJsonNode = objectMapper.readTree(releaseJsonString);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        return releaseJsonNode;
    }

    public String getTitle() {
        return String.format(Constants.RELEASE_REVISION_NAMESPACE_FORMAT,
                namespaceSecretReleaseRevision.release(),
                namespaceSecretReleaseRevision.revision(),
                namespaceSecretReleaseRevision.namespace().getMetadata().getName());
    }

    public String getChartInfo() {
        if (chartInfo == null) {
            JsonNode releaseJsonNode = getReleaseJsonNode();
            chartInfo = String.format("Chart: %s\nStatus: %s\n",
                    releaseJsonNode.get("name").asText(),
                    releaseJsonNode.get("info").get("status").asText());
        }
        return chartInfo;
    }

    public String getValues() {
        if (values == null) {
            try {
                JsonNode releaseJsonNode = getReleaseJsonNode();
                JsonNode valuesNode = releaseJsonNode.get("chart").get("values");
                values  = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(valuesNode);
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        }
        return values;
    }

    public String getTemplates() {
        if (templates == null) {
            JsonNode releaseJsonNode = getReleaseJsonNode();
            StringBuilder templatesStringBuilder = new StringBuilder();
            ArrayNode templatesArrayNode = (ArrayNode) releaseJsonNode.get("chart").get("templates");
            templatesArrayNode.forEach(template -> {
                templatesStringBuilder.append("Template: ");
                templatesStringBuilder.append(template.get("name").asText());
                templatesStringBuilder.append("\n");
                templatesStringBuilder.append(new String(Base64Coder.decode(template.get("data").asText()), StandardCharsets.UTF_8));
                templatesStringBuilder.append("\n");
                templatesStringBuilder.append("----\n");
            });
            templates = templatesStringBuilder.toString();
        }
        return templates;
    }

    public Map<String, String> getTemplatesMap() {
        Map<String, String> templatesMap = new TreeMap<>();
        String templates = getTemplates();
        String[] templatesLines = templates.split("\\r?\\n");
        String[] template = new String[1];
        List<String> aTemplateLines = new LinkedList<>();;
        Arrays.stream(templatesLines).forEach((String templateLine) -> {
            if (templateLine.startsWith("Template: ")) {
                if (template[0] != null) {
                    templatesMap.put(template[0], aTemplateLines.stream().collect(Collectors.joining("\n")));
                    aTemplateLines.clear();
                }
                template[0] = templateLine.substring(10);
            } else {
                aTemplateLines.add(templateLine);
            }
        });
        if (template[0] != null) {
            templatesMap.put(template[0], Arrays.stream(templatesLines).collect(Collectors.joining("\n")));
        }
        return templatesMap;
    }

    public String getManifests() {
        if (manifests == null) {
            JsonNode releaseJsonNode = getReleaseJsonNode();
            manifests = releaseJsonNode.get("manifest").asText().replace("\\n", "\n");
        }
        return manifests;
    }

    public Map<String, String> getManifestsMap() {
        Map<String, String> manifestsMap = new TreeMap<>();
        String manifests = getManifests();
        return manifestsMap;
    }

    public String getHooks() {
        if (hooks == null) {
            JsonNode releaseJsonNode = getReleaseJsonNode();
            StringBuilder hooksStringBuilder = new StringBuilder();
            ArrayNode hooksArrayNode = (ArrayNode) releaseJsonNode.get("hooks");
            hooksArrayNode.forEach(hook -> {
                hooksStringBuilder.append(String.format("Hook: %s Events: %s\n", hook.get("path").asText(), hook.get("events")));
                hooksStringBuilder.append(hook.get("manifest").asText().replace("\\n", "\n"));
                hooksStringBuilder.append("----\n");
            });
            hooks = hooksStringBuilder.toString();
        }
        return hooks;
    }

    public Map<String, String> getHooksMap() {
        Map<String, String> hooksMap = new TreeMap<>();
        String hooks = getHooks();
        return hooksMap;
    }

    public String getNotes() {
        if (notes == null) {
            JsonNode releaseJsonNode = getReleaseJsonNode();
            notes = releaseJsonNode.get("info").get("notes").asText().replace("\\n", "\n");
        }
        return notes;
    }
}
