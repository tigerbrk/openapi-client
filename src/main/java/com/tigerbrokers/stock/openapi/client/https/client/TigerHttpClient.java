package com.tigerbrokers.stock.openapi.client.https.client;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.tigerbrokers.stock.openapi.client.TigerApiException;
import com.tigerbrokers.stock.openapi.client.struct.enums.TigerApiCode;
import com.tigerbrokers.stock.openapi.client.constant.TigerApiConstants;
import com.tigerbrokers.stock.openapi.client.util.TigerSignature;
import com.tigerbrokers.stock.openapi.client.https.request.TigerHttpRequest;
import com.tigerbrokers.stock.openapi.client.https.request.TigerRequest;
import com.tigerbrokers.stock.openapi.client.https.response.TigerHttpResponse;
import com.tigerbrokers.stock.openapi.client.https.response.TigerResponse;
import com.tigerbrokers.stock.openapi.client.util.StringUtils;
import com.tigerbrokers.stock.openapi.client.util.WebUtils;

import java.net.SocketTimeoutException;
import java.security.Security;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.tigerbrokers.stock.openapi.client.constant.TigerApiConstants.*;

/**
 * HTTP客户端
 */
public class TigerHttpClient implements TigerClient {

  private static final Logger logger = LoggerFactory.getLogger(TigerHttpClient.class);

  private String serverUrl;
  private String tigerId;
  private String privateKey;
  private String tigerPublicKey;
  private String signType = TigerApiConstants.SIGN_TYPE_RSA;
  private String charset = TigerApiConstants.CHARSET_UTF8;

  private int connectTimeout = 10000;
  private int readTimeout = 15000;

  static {
    Security.setProperty("jdk.certpath.disabledAlgorithms", "");
  }

  public TigerHttpClient(String serverUrl, String tigerId, String privateKey, String tigerPublicKey) {
    this.serverUrl = serverUrl;
    this.tigerId = tigerId;
    this.privateKey = privateKey;
    this.tigerPublicKey = tigerPublicKey;
  }

  public <T extends TigerResponse> T execute(TigerRequest<T> request) {
    if (request instanceof TigerHttpRequest) {
      return (T) execute((TigerHttpRequest) request);
    }
    return null;
  }

  public TigerHttpResponse execute(TigerHttpRequest request) {
    TigerHttpResponse response;
    try {
      String data = doPost(request);
      if (StringUtils.isEmpty(data)) {
        return null;
      }
      response = JSON.parseObject(data, TigerHttpResponse.class);
      if (StringUtils.isEmpty(this.tigerPublicKey)) {
        return response;
      }
      if (response.getSign() == null) {
        return response;
      }
      boolean signSuccess =
          TigerSignature.rsaCheckContent(request.getTimestamp(), response.getSign(), this.tigerPublicKey, this.charset);
      if (!signSuccess) {
        throw new TigerApiException(TigerApiCode.SIGN_CHECK_FAILED);
      }
    } catch (TigerApiException e) {
      logger.error("client execute api exeception:", e);
      return TigerHttpResponse.errorMsg(e.getApiError());
    } catch (Exception e) {
      logger.error("client execute exception:", e);
      return TigerHttpResponse.errorMsg(TigerApiCode.CLIENT_API_ERROR);
    }

    return response;
  }

  private String doPost(TigerHttpRequest request) throws TigerApiException {
    if (StringUtils.isEmpty(request.getApiMethodName())) {
      throw new TigerApiException(TigerApiCode.METHOD_NAME_ERROR);
    }
    try {
      return WebUtils.doPost(serverUrl, CONTENT_TYPE_JSON, JSONObject.toJSONString(buildParams(request)),
          connectTimeout, readTimeout);
    } catch (TigerApiException e) {
      logger.error("client post request tiger api exception:", e);
      throw new TigerApiException(e.getMessage());
    } catch (SocketTimeoutException e) {
      logger.error("client post request socket time out exception:", e);
      throw new TigerApiException(TigerApiCode.READ_TIME_OUT);
    } catch (Exception e) {
      logger.error("client post request exception:", e);
      throw new TigerApiException(e.getMessage());
    }
  }

  private Map<String, Object> buildParams(TigerHttpRequest request) throws TigerApiException {
    Map params = new HashMap<>();
    params.put(METHOD, request.getApiMethodName());
    params.put(VERSION, request.getApiVersion());
    params.put(TIGER_ID, this.tigerId);
    params.put(SIGN_TYPE, this.signType);
    params.put(CHARSET, this.charset);
    params.put(TIMESTAMP, request.getTimestamp());
    params.put(BIZ_CONTENT, request.getBizContent());
    String signContent = TigerSignature.getSignContent(params);
    String sign = TigerSignature.rsaSign(signContent, privateKey, charset);
    params.put(SIGN, sign);
    return params;
  }
}
