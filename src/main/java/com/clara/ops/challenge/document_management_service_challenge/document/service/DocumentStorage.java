package com.clara.ops.challenge.document_management_service_challenge.document.service;

import java.io.InputStream;

public interface DocumentStorage {

  void upload(String objectKey, InputStream inputStream, long size, String contentType);

  String generatePresignedGetUrl(String objectKey);

  String generatePresignedPutUrl(String objectKey);

  String getBucket();
}
