package app;

import app.model.Station;
import app.model.Stop;
import app.model.Train;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.ext.bridge.BridgeEventType;
import io.vertx.ext.bridge.PermittedOptions;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.sockjs.BridgeOptions;
import io.vertx.ext.web.handler.sockjs.SockJSHandler;
import org.infinispan.client.hotrod.RemoteCache;
import org.infinispan.client.hotrod.RemoteCacheManager;
import org.infinispan.client.hotrod.configuration.ConfigurationBuilder;
import org.infinispan.client.hotrod.marshall.ProtoStreamMarshaller;
import org.infinispan.protostream.FileDescriptorSource;
import org.infinispan.protostream.SerializationContext;
import org.infinispan.query.remote.client.ProtobufMetadataManagerConstants;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringWriter;
import java.util.logging.Logger;

import static org.infinispan.query.remote.client.ProtobufMetadataManagerConstants.PROTOBUF_METADATA_CACHE_NAME;

public class AppUtils {

  static final Logger log = Logger.getLogger(AppUtils.class.getName());

  static RemoteCacheManager createRemoteCacheManager() {
    RemoteCacheManager client = new RemoteCacheManager(
      new ConfigurationBuilder().addServer()
        .host("datagrid-hotrod")
        .port(11222)
        .marshaller(ProtoStreamMarshaller.class)
        .build());

    SerializationContext ctx = ProtoStreamMarshaller.getSerializationContext(client);
    try {
      ctx.registerProtoFiles(FileDescriptorSource.fromResources("app-model.proto"));
      ctx.registerMarshaller(new Stop.Marshaller());
      ctx.registerMarshaller(new Station.Marshaller());
      ctx.registerMarshaller(new Train.Marshaller());
      addModelToServer(client);
      return client;
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private static void addModelToServer(RemoteCacheManager client) {
    InputStream is = AppUtils.class.getResourceAsStream("/app-model.proto");
    RemoteCache<String, String> metaCache = client.getCache(PROTOBUF_METADATA_CACHE_NAME);
    metaCache.put("app-model.proto", readInputStream(is));

    String errors = metaCache.get(ProtobufMetadataManagerConstants.ERRORS_KEY_SUFFIX);
    if (errors != null)
      throw new RuntimeException("Error in proto file");
  }

  static Handler<RoutingContext> sockJSHandler(Vertx vertx) {
    SockJSHandler sockJSHandler = SockJSHandler.create(vertx);
    PermittedOptions outPermit = new PermittedOptions().setAddress("delayed-trains");
    BridgeOptions options = new BridgeOptions().addOutboundPermitted(outPermit);
    sockJSHandler.bridge(options, be -> {
      if (be.type() == BridgeEventType.REGISTER)
        log.info("SockJs: client connected");

      be.complete(true);
    });
    return sockJSHandler;
  }

  private static String readInputStream(InputStream is) {
    try {
      try {
        final Reader reader = new InputStreamReader(is, "UTF-8");
        StringWriter writer = new StringWriter();
        char[] buf = new char[1024];
        int len;
        while ((len = reader.read(buf)) != -1) {
          writer.write(buf, 0, len);
        }
        return writer.toString();
      } finally {
        is.close();
      }
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

}