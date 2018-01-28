package com.binance.api.client.impl;

import com.binance.api.client.BinanceApiCallback;
import com.binance.api.client.BinanceApiWebSocketClient;
import com.binance.api.client.constant.BinanceApiConstants;
import com.binance.api.client.domain.event.AggTradeEvent;
import com.binance.api.client.domain.event.CandlestickEvent;
import com.binance.api.client.domain.event.DepthEvent;
import com.binance.api.client.domain.event.UserDataUpdateEvent;
import com.binance.api.client.domain.market.CandlestickInterval;
import okhttp3.OkHttpClient;
import okhttp3.Dispatcher;
import okhttp3.Request;
import okhttp3.WebSocket;

import java.io.Closeable;
import java.io.EOFException;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Binance API WebSocket client implementation using OkHttp.
 */
public class BinanceApiWebSocketClientImpl implements BinanceApiWebSocketClient, Closeable {

  private OkHttpClient client;
  private Map<String, WebSocket> openSocketMap;

  public BinanceApiWebSocketClientImpl() {
    Dispatcher d = new Dispatcher();
    d.setMaxRequestsPerHost(100);
    openSocketMap = new HashMap<>();
    this.client = new OkHttpClient.Builder().dispatcher(d).build();
  }

  public void onDepthEvent(String symbol, BinanceApiCallback<DepthEvent> callback) {
    final String channel = String.format("%s@depth", symbol);
    createNewWebSocket(channel, new BinanceApiWebSocketListener<>(callback, DepthEvent.class));
  }

  @Override
  public void onCandlestickEvent(String symbol, CandlestickInterval interval, BinanceApiCallback<CandlestickEvent> callback) {
    final String channel = String.format("%s@kline_%s", symbol, interval.getIntervalId());
    createNewWebSocket(channel, new BinanceApiWebSocketListener<>(callback, CandlestickEvent.class));
  }

  public void onAggTradeEvent(String symbol, BinanceApiCallback<AggTradeEvent> callback) {
    final String channel = String.format("%s@aggTrade", symbol);
    createNewWebSocket(channel, new BinanceApiWebSocketListener<>(callback, AggTradeEvent.class));
  }

  public void onUserDataUpdateEvent(String listenKey, BinanceApiCallback<UserDataUpdateEvent> callback) {
    createNewWebSocket(listenKey, new BinanceApiWebSocketListener<>(callback, UserDataUpdateEvent.class));
  }

  @Override
  public void close(BinanceEventType type, String ticker) throws EOFException {
    WebSocket socket = openSocketMap.getOrDefault(getSocketMapKey(type.toString(), ticker),null);
    if (socket !=null){
        socket.close(3000,"close requested");
    }
  }

  private void createNewWebSocket(String channel, BinanceApiWebSocketListener<?> listener) {
    String streamingUrl = String.format("%s/%s", BinanceApiConstants.WS_API_BASE_URL, channel);
    Request request = new Request.Builder().url(streamingUrl).build();
    WebSocket thisSocket = client.newWebSocket(request, listener);
    String listenerType = listener.getEventClassName();
    if(!listenerType.equals(BinanceEventType.UserDataUpdateEvent.toString())){
      String symbol = channel.split("@")[0];
      openSocketMap.put(getSocketMapKey(listenerType,symbol),thisSocket);
    }
  }

  public String getSocketMapKey(String type, String ticker){
    return type+"@"+ticker;
  }
  @Override
  public void close() throws IOException {
    client.dispatcher().executorService().shutdown();
  }

  public enum BinanceEventType{
    AggTradeEvent,DepthEvent,CandleStickEvent,UserDataUpdateEvent
  }
}
