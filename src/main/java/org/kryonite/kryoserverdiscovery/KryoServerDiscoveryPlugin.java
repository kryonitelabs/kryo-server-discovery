package org.kryonite.kryoserverdiscovery;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.ProxyServer;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import lombok.extern.slf4j.Slf4j;
import org.kryonite.kryoserverdiscovery.listener.PlayerJoinListener;
import org.kryonite.kryoserverdiscovery.serverdiscovery.ServerDiscoveryTask;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.Timer;

@Slf4j
@Plugin(id = "kryoserverdiscovery", name = "Kryo Server Discovery", version = "1.0.0")
public class KryoServerDiscoveryPlugin {

  private final Timer timer = new Timer(true);
  private final ProxyServer server;
  private final Map<String, String> configuration = new HashMap<>();

  private final Set<String> discoveredServers = new HashSet<>();
  private static final Map<String, String> configurationDefaults = new HashMap<>();

  static {
    configurationDefaults.put("enable-join-listener", "true");
    configurationDefaults.put("discover-task-interval-ms", "1000");
    configurationDefaults.put("server-name-format", "%s");
  }

  @Inject
  public KryoServerDiscoveryPlugin(ProxyServer server) {
    this.server = server;
  }

  @Subscribe
  public void onInitialize(ProxyInitializeEvent event) {

    this.loadConfiguration(System.getenv());

    log.info("Following configuration was parsed:");
    this.configuration.forEach((k, v) -> log.info(String.format("%s: %s", k, v)));

    DefaultKubernetesClient kubernetesClient = new DefaultKubernetesClient();
    timer.scheduleAtFixedRate(new ServerDiscoveryTask(server, kubernetesClient, this.configuration, this.discoveredServers), 1000, Long.parseLong(this.configuration.get("discover-task-interval-ms")));

    if (Boolean.parseBoolean(this.configuration.get("enable-join-listener"))) {
      server.getEventManager().register(this, new PlayerJoinListener(this, server));
    }
  }

  public Set<String> getDiscoveredServers() {
    return new HashSet<>(discoveredServers);
  }

  public String getConfigEntry(String key) {
    return this.configuration.get(key);
  }

  public void loadConfiguration(Map<String, String> configuration) {
    Map<String, String> envDirectives = new HashMap<>();
    configuration.entrySet()
      .stream()
      .filter(entry -> entry.getKey().startsWith("KRYO_SV_"))
      .forEach(entry -> {
        // This transforms the key from the standard environment var format to our configuration directive format
        // Example: KRYO_SV_ENABLE_JOIN_LISTENER -> enable-join-listener
        String key = entry.getKey()
          .replaceFirst("KRYO_SV_", "")
          .toLowerCase().replace("_", "-");
        envDirectives.put(key, entry.getValue());
      });

    configurationDefaults
      .keySet()
      .forEach(key -> this.configuration.put(key, envDirectives.getOrDefault(key, configurationDefaults.get(key))));
  }
}
