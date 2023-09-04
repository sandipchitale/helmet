package sandipchitale.helmet;

import io.fabric8.kubernetes.api.model.Namespace;
import io.fabric8.kubernetes.api.model.Secret;

record Tuple(Namespace namespace, Secret secret, String release, String revision) {}
