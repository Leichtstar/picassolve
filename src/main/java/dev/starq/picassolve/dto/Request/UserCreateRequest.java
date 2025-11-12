package dev.starq.picassolve.dto.Request;

public record UserCreateRequest(
	String name,
	String password,
	int team
){

}
