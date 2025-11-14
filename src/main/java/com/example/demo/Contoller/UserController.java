package com.example.demo.Contoller;

import com.example.demo.Dto.UserDto;
import com.example.demo.Service.UserService;
import com.example.demo.model.User;
import jakarta.validation.Valid;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("api/users")
@RequiredArgsConstructor
public class UserController {
private  final UserService userService;

@PostMapping
public ResponseEntity<UserDto> create(@Valid @RequestBody UserDto dto)
{
    UserDto user =  userService.create(dto);
    return ResponseEntity.ok(user);
}
@GetMapping
public ResponseEntity<List<UserDto>> getAll(){
    return ResponseEntity.ok(userService.findAllUsers());
}
}
