package com.safori.domain.organization.model;

/** 기관별 인원 수(운영자 기관 목록·상세용). */
public record OrganizationCount(Long organizationId, long count) {
}
