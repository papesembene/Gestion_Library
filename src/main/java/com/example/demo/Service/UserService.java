package com.example.demo.Service;

import com.example.demo.Dto.UserDto;
import com.example.demo.Repository.RoleRepository;
import com.example.demo.Repository.UserRepository;
import com.example.demo.model.Role;
import com.example.demo.model.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserService {

    private  final UserRepository userRepository;

    private final  RoleRepository roleRepository;

    public UserDto create(UserDto userDto) {
        User user = convertToEntity(userDto);
        User savedUser = userRepository.save(user);
        return convertToDTO(savedUser);
    }

    public List<UserDto> findAllUsers() {
        return userRepository.findAll().stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    private UserDto convertToDTO(User user) {
        return UserDto.builder()
                .id(user.getId())
                .nom(user.getNomComplet())
                .email(user.getEmail())
                .roleId(user.getRole() != null ? user.getRole().getId() : null)
                .build();
    }

    private User convertToEntity(UserDto userDTO) {
        Role role = roleRepository.findById(userDTO.getRoleId())
                .orElseThrow(() -> new RuntimeException("Role not found"));
        return User.builder()
                .id(userDTO.getId())
                .nomComplet(userDTO.getNom())
                .email(userDTO.getEmail())
                .password(userDTO.getPassword())
                .role(role)
                .build();
    }
}
