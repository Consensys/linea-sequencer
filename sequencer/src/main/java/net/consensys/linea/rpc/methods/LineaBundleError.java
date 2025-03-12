package net.consensys.linea.rpc.methods;

import lombok.RequiredArgsConstructor;
import org.hyperledger.besu.plugin.services.rpc.RpcMethodError;

@RequiredArgsConstructor
class LineaBundleError implements RpcMethodError {

  final String errMessage;
  final int errCode;

  @Override
  public int getCode() {
    return errCode;
  }

  @Override
  public String getMessage() {
    return errMessage;
  }

  public static LineaBundleError invalidParams(final String errMessage) {
    return new LineaBundleError(errMessage, INVALID_PARAMS_ERROR_CODE);
  }

  public static LineaBundleError invalidTransaction(final String errMessage) {
    return new LineaBundleError(errMessage, -32000);
  }
}
