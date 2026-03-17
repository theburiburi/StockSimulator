package com.stock.stockSimulator.domain;

import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Entity
@NoArgsConstructor
@Getter
public class Member {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String name;

    @Column(unique = true)
    private String email;
    private Long balance;

    @Enumerated(EnumType.STRING)
    private Role role;

    @Builder public Member(String name, String email, Role role){
        this.name = name;
        this.email = email;
        this.role = role;
        this.balance = 10_000_000L;
    }

    public void decreaseBalance(long amount){
        this.balance -= amount;
    }

    public void increaseBalance(long amount){
        this.balance += amount;
    }
}
