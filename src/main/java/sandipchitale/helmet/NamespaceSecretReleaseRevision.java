package sandipchitale.helmet;

import io.fabric8.kubernetes.api.model.Secret;
import io.kubernetes.client.openapi.models.V1Secret;

public record NamespaceSecretReleaseRevision(String namespace, Secret secret, V1Secret v1Secret, String release, String revision) {}
