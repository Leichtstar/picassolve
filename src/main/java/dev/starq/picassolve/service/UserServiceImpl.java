package dev.starq.picassolve.service;

import dev.starq.picassolve.dto.Request.UserCreateRequest;
import dev.starq.picassolve.dto.UserDto;
import dev.starq.picassolve.entity.User;
import dev.starq.picassolve.entity.UserMapper;
import dev.starq.picassolve.repository.UserRepository;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class UserServiceImpl implements UserService {

	private final UserRepository userRepository;
	private final UserMapper userMapper;
	private final PasswordEncoder passwordEncoder;

	// --- 회원 가입 및 조회 ---

	@Override
	public UserDto create(UserCreateRequest request) {
		Objects.requireNonNull(request, "request must not be null");
		String name = normalize(request.name());
		if (name.isBlank()) {
			throw new IllegalArgumentException("이름을 입력해주세요.");
		}
		if (userRepository.existsByName(name)) {
			throw new IllegalArgumentException("이미 존재하는 이름입니다: " + name);
		}

		String rawPassword = normalize(request.password());
		if (rawPassword.isBlank()) {
			throw new IllegalArgumentException("비밀번호를 입력해주세요.");
		}
		int team = request.team();
		if (team < 0) {
			throw new IllegalArgumentException("팀 번호는 0 이상이어야 합니다.");
		}

		User createdUser = User.builder()
				.name(name)
				.password(passwordEncoder.encode(rawPassword))
				.team(team)
				.build();

		User saved = userRepository.save(createdUser);
		log.info("[사용자] 신규 가입 완료: {} (ID: {}, 팀: {})", saved.getName(), saved.getId(), saved.getTeam());
		return userMapper.toDto(saved);
	}

	@Override
	@Transactional(readOnly = true)
	public UserDto findByName(String name) {
		return userRepository.findByName(normalize(name))
				.map(userMapper::toDto)
				.orElse(null);
	}

	@Override
	@Transactional(readOnly = true)
	public List<UserDto> findAll() {
		return userRepository.findAll().stream()
				.map(userMapper::toDto)
				.toList();
	}

	// --- 프로필 관리 ---

	@Override
	public UserDto updateProfile(String currentName, String newName, Integer newTeam, String password) {
		User user = userRepository.findByName(normalize(currentName))
				.orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

		if (!passwordEncoder.matches(normalize(password), user.getPassword())) {
			throw new IllegalArgumentException("비밀번호가 일치하지 않습니다.");
		}

		// 이름 변경
		String normalizedNewName = normalize(newName);
		if (!normalizedNewName.isBlank() && !normalizedNewName.equals(user.getName())) {
			if (userRepository.existsByName(normalizedNewName)) {
				throw new IllegalArgumentException("이미 존재하는 이름입니다.");
			}
			user.setName(normalizedNewName);
		} else if (normalizedNewName.isBlank()) {
			throw new IllegalArgumentException("이름을 입력해 주세요.");
		}

		// 팀 변경
		if (newTeam != null) {
			if (newTeam < 0) {
				throw new IllegalArgumentException("팀 번호는 0 이상이어야 합니다.");
			}
			user.setTeam(newTeam);
		}

		User saved = userRepository.save(user);
		log.info("[사용자] 프로필 수정 완료: {} (ID: {})", saved.getName(), saved.getId());
		return userMapper.toDto(saved);
	}

	@Override
	public void changePassword(String username, String currentPassword, String newPassword) {
		String normalizedName = normalize(username);
		String normalizedCurrent = normalize(currentPassword);
		String normalizedNew = normalize(newPassword);

		if (normalizedCurrent.isBlank() || normalizedNew.isBlank()) {
			throw new IllegalArgumentException("현재 비밀번호와 새 비밀번호를 모두 입력해 주세요.");
		}
		if (normalizedCurrent.equals(normalizedNew)) {
			throw new IllegalArgumentException("새 비밀번호가 기존 비밀번호와 동일합니다.");
		}

		User user = userRepository.findByName(normalizedName)
				.orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

		if (!passwordEncoder.matches(normalizedCurrent, user.getPassword())) {
			throw new IllegalArgumentException("현재 비밀번호가 일치하지 않습니다.");
		}

		user.setPassword(passwordEncoder.encode(normalizedNew));
		userRepository.save(user);
		log.info("[사용자] 비밀번호 변경 완료: {}", normalizedName);
	}

	// --- 계정 삭제 ---

	@Override
	public void deleteUser(String username, String password) {
		User user = userRepository.findByName(normalize(username))
				.orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

		if (!passwordEncoder.matches(normalize(password), user.getPassword())) {
			throw new IllegalArgumentException("비밀번호가 일치하지 않습니다.");
		}

		userRepository.delete(user);
		log.info("[사용자] 계정 본인 삭제 완료: {}", username);
	}

	@Override
	public void delete(UUID id) {
		if (id == null)
			return;
		userRepository.findById(id).ifPresent(user -> {
			userRepository.delete(user);
			log.info("[사용자] 계정 강제 삭제 완료 (ID: {}, 이름: {})", id, user.getName());
		});
	}

	private String normalize(String value) {
		return value == null ? "" : value.trim();
	}
}
