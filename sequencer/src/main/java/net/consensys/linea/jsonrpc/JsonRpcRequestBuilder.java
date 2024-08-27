package net.consensys.linea.jsonrpc;

import com.google.gson.JsonObject;

public class JsonRpcRequestBuilder {
  public static String buildRequest(final String method, final JsonObject params, int id) {
    JsonObject request = new JsonObject();
    request.addProperty("jsonrpc", "2.0");
    request.addProperty("method", method);
    request.add("params", params);
    request.addProperty("id", id);
    return request.toString();
  }
}
