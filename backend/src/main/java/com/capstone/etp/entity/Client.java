package com.capstone.etp.entity;

import com.capstone.etp.enums.ClientStatus;
import com.capstone.etp.enums.Gender;
import jakarta.persistence.*;
import jakarta.validation.constraints.Pattern;
import lombok.*;

import java.time.LocalDate;
import java.util.List;


@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Client {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 10)
    private String clientRef;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String passwordHash;

    @Column(length = 10)
    @Pattern(regexp = "\\d{10}")
    private String phone;

    private LocalDate dateOfBirth;

    @Enumerated(EnumType.STRING)
    private Gender gender;

    @Enumerated(EnumType.STRING)
    private ClientStatus status;

    @OneToMany(
            mappedBy = "client",
            cascade = CascadeType.ALL
    )
    private List<Account> accounts;

    public boolean isActive(){
        return status == ClientStatus.ACTIVE;
    }

    public void register(){}
    public void login(){}

    public void updateProfile(
            String name,
            String phone,
            Gender gender
    ){
        this.name = name;
        this.phone = phone;
        this.gender = gender;
    }
}