// Copyright (c) 2026 ByteDance Ltd. and/or its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.volcengine.ark.runtime.utils;

import java.io.File;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;

public class MultipartBodyUtils
{
    public static final MediaType TYPE = MediaType.parse("multipart/form-data");
    public static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    private MultipartBodyUtils()
    {
    }

    public static MultipartBody.Part getPart(File image, String name)
    {
        RequestBody body = RequestBody.create(TYPE, image);
        return MultipartBody.Part.createFormData(name, image.getName(), body);
    }
}
