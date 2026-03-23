package com.stock.stockSimulator.dto.request;

import com.stock.stockSimulator.domain.Role;
import lombok.Data;

/**
 * 회원 생성 요청 DTO
 * SRP: Controller에서 정의하던 Inner class를 분리
 */
@Data
public class CreateMemberRequest {
    private String name;
    private String email;
    private Role role;
}
