package dev.starq.picassolve.service;

import dev.starq.picassolve.dto.Request.UserCreateRequest;
import dev.starq.picassolve.dto.UserDto;
import java.util.List;
import java.util.UUID;

public interface UserService {
	// --- 회원 가입 및 조회 ---
	UserDto create(UserCreateRequest request);

	UserDto findByName(String name);

	List<UserDto> findAll();

	// --- 프로필 관리 ---
	UserDto updateProfile(String currentName, String newName, Integer newTeam, String password);

	void changePassword(String username, String currentPassword, String newPassword);

	// --- 계정 삭제 ---
	void deleteUser(String username, String password);

	void delete(UUID id); // 관리자용 등 ID 기반 삭제
}
