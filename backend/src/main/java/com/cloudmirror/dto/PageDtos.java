package com.cloudmirror.dto;

import java.util.List;

/** 通用分页返回：{ items, total, page, size } */
public record PageDtos<T>(List<T> items, long total, int page, int size) {
}
