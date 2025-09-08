package dev.starq.picassolve;

import org.springframework.boot.SpringApplication;     // 스프링 부트 런처
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication                                 // 컴포넌트 스캔 + 자동설정
public class PicassolveApplication {
    public static void main(String[] args) {
        SpringApplication.run(PicassolveApplication.class, args); // 내장서버 기동
    }
}
