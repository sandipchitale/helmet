package sandipchitale.helmet;

import io.fabric8.kubernetes.api.model.Namespace;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.kubernetes.client.openapi.models.V1Secret;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;

public class HelmReleaseRevisionSecretsAccessor {
    static KubernetesClient kubernetesClient = new KubernetesClientBuilder().build();

    static Set<NamespaceSecretReleaseRevision> getNamespaceSecretReleaseRevisionSetAllNamespaces() {
        Set<NamespaceSecretReleaseRevision> namespaceStringStringNamespaceSecretReleaseRevisionSet = new LinkedHashSet<>();
        kubernetesClient
                .namespaces()
                .list()
                .getItems()
                .forEach((Namespace namespace) -> {
                    namespaceStringStringNamespaceSecretReleaseRevisionSet.addAll(
                            getNamespaceSecretReleaseRevisionSetInNamespace(namespace.getMetadata().getName()));
                });
        return namespaceStringStringNamespaceSecretReleaseRevisionSet;
    }

    static Set<NamespaceSecretReleaseRevision> getNamespaceSecretReleaseRevisionSetInNamespace(String namespace) {
        Set<NamespaceSecretReleaseRevision> namespaceStringStringNamespaceSecretReleaseRevisionSet = new LinkedHashSet<>();
        kubernetesClient
                .secrets()
                .inNamespace(namespace)
                .list()
                .getItems()
                .stream()
                .filter(secret -> {
                    Matcher matcher = Constants.helmSecretNamePattern.matcher(secret.getMetadata().getName());
                    return (matcher.matches());
                })
                .forEach(secret -> {
                    Matcher matcher = Constants.helmSecretNamePattern.matcher(secret.getMetadata().getName());
                    if (matcher.matches()) {
                        String release = matcher.group(1);
                        String revision = matcher.group(2);
                        namespaceStringStringNamespaceSecretReleaseRevisionSet.add(new NamespaceSecretReleaseRevision(namespace, secret, null, release, revision));
                    }
                });

        return namespaceStringStringNamespaceSecretReleaseRevisionSet;
    }

    static Set<NamespaceSecretReleaseRevision> getNamespaceSecretReleaseRevisionSetFromV1Secret(V1Secret secret) {
        Set<NamespaceSecretReleaseRevision> namespaceStringStringNamespaceSecretReleaseRevisionSet = new LinkedHashSet<>();
        Matcher matcher = Constants.helmSecretNamePattern.matcher(Objects.requireNonNull(Objects.requireNonNull(secret.getMetadata()).getName()));
        if (matcher.matches()) {
            String release = matcher.group(1);
            String revision = matcher.group(2);
            namespaceStringStringNamespaceSecretReleaseRevisionSet.add(new NamespaceSecretReleaseRevision(
                    secret.getMetadata().getNamespace(),
                    kubernetesClient.secrets().inNamespace(secret.getMetadata().getNamespace()).withName(secret.getMetadata().getName()).get(),
                    null,
                    release,
                    revision));
        }
        return namespaceStringStringNamespaceSecretReleaseRevisionSet;
    }
}