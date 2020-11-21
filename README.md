# SpringSecurity入门
本项目是基于数据库认证，初学建议先参考springboot-security-demo-v1

## 数据库脚本

```sql
DROP TABLE IF EXISTS `role`;
CREATE TABLE `role` (
`id` int(11) NOT NULL AUTO_INCREMENT,
`name` varchar(32) DEFAULT NULL,
`nameZh` varchar(32) DEFAULT NULL,
PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8;

INSERT INTO `role` VALUES ('1', 'ROLE_DBA', '数据库管理员');
INSERT INTO `role` VALUES ('2', 'ROLE_ADMIN', '系统管理员');
INSERT INTO `role` VALUES ('3', 'ROLE_USER', '用户');


DROP TABLE IF EXISTS `user`;
CREATE TABLE `user` (
`id` int(11) NOT NULL AUTO_INCREMENT,
`username` varchar(32) DEFAULT NULL,
`password` varchar(255) DEFAULT NULL,
`enabled` tinyint(1) DEFAULT NULL,
`locked` tinyint(1) DEFAULT NULL,
PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8;

INSERT INTO `user` VALUES ('1', 'root', '$2a$10$RMuFXGQ5AtH4wOvkUqyvuecpqUSeoxZYqilXzbz50dceRsga.WYiq', '1', '0');
INSERT INTO `user` VALUES ('2', 'admin', '$2a$10$RMuFXGQ5AtH4wOvkUqyvuecpqUSeoxZYqilXzbz50dceRsga.WYiq', '1', '0');
INSERT INTO `user` VALUES ('3', 'sang', '$2a$10$RMuFXGQ5AtH4wOvkUqyvuecpqUSeoxZYqilXzbz50dceRsga.WYiq', '1', '0');

DROP TABLE IF EXISTS `user_role`;
CREATE TABLE `user_role` (
`id` int(11) NOT NULL AUTO_INCREMENT,
`uid` int(11) DEFAULT NULL,
`rid` int(11) DEFAULT NULL,
PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8;

INSERT INTO `user_role` VALUES ('1', '1', '1');
INSERT INTO `user_role` VALUES ('2', '1', '2');
INSERT INTO `user_role` VALUES ('3', '2', '2');
INSERT INTO `user_role` VALUES ('4', '3', '3');
```

## 加密

密码是123 springsecurity加密算法后的密文，可以让同样的密码每次生成的密文都不一样。

```java
@Bean
    PasswordEncoder passwordEncoder(){
        //密码加密
        return new BCryptPasswordEncoder();
    }
```

## 创建实体类

### Role

```java
package com.security.model;

public class Role {
    private Integer id;
    private String name;
    private String nameZh;
    
    //get/set
}

```



### User

User实现UserDetails，这个接口好比一个规范。防止开发者定义的密码变量名各不相同，从而导致springSecurity不知道哪个方法是你的密码

```java
package com.security.model;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class User implements UserDetails{
    private  Integer id;
    private  String username;
    private  String password;
    private  boolean enabled;
    private  boolean locked;

    private List<Role> roleList;

    //get/set
  
}

```

我给的数据库中user表没有UserDetails的几个方法，可以直接手动给它true

```java
@Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return !locked;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
```

有的就是用数据库查出来的属性

```java
@Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        List<SimpleGrantedAuthority> authorities = new ArrayList<>();
        for (Role role: roleList) {
            authorities.add(new SimpleGrantedAuthority(role.getName()));
        }
        return authorities;
    }
```

## Mapper&Service

### UserMapper

```java 
package com.security.mapper;

import com.security.model.Role;
import com.security.model.User;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface UserMapper {
    User getUserByName(String name);

    List<Role> getRolesById(Integer id);
}
```

### UserMapper.xml

resource/mapper目录下

```xml
<?xml version="1.0" encoding="UTF-8" ?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN" "http://mybatis.org/dtd/mybatis-3-mapper.dtd" >
<mapper namespace="com.security.mapper.UserMapper">
    <select id="getUserByName" resultType="User">
        SELECT * FROM user WHERE username = #{username};
    </select>

    <select id="getRolesById" resultType="Role">
        SELECT * FROM role WHERE id in (SELECT rid from user_role WHERE uid = #{uid})
    </select>
</mapper>
```

### springboot配置mybatis

```yml
server:
  port: 8081
spring:
  datasource:
    driver-class-name: com.mysql.cj.jdbc.Driver
    url: jdbc:mysql://localhost:3306/test2?serverTimezone=UTC
    username: root
    password: accp

mybatis:
  mapper-locations: classpath:mapper/*.xml
  type-aliases-package: com.security.model
  configuration:
    map-underscore-to-camel-case: true

```

### 添加扫描

```java
package com.security;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.security.mapper")//使用MapperScan批量扫描所有的Mapper接口；
public class SpringbootSecurityApplication {
	public static void main(String[] args) {
		SpringApplication.run(SpringbootSecurityApplication.class, args);
	}
}

```

### UserService

实现UserDetailsService接口

```java
package com.security.service;

import com.security.mapper.UserMapper;
import com.security.model.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class UserService implements UserDetailsService{
    @Autowired
    private  UserMapper userMapper;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userMapper.getUserByName(username);
        if (user == null){
            throw new UsernameNotFoundException("username not exist");
        }
        user.setRoleList(userMapper.getRolesById(user.getId()));
        return user;
    }
}

```

## Security配置类

之前有基于内存认证的经验，这一步就很简单了，修改下WebSecurityConfigurerAdapter的configure方法即可

```java
package com.security.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.security.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.*;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.NoOpPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableGlobalMethodSecurity(prePostEnabled = true,securedEnabled = true)
public class MyWebSecurityConfig extends WebSecurityConfigurerAdapter {
    @Autowired
    private UserService userService;

    @Bean
    PasswordEncoder passwordEncoder(){
        //密码加密
        return new BCryptPasswordEncoder();
    }

    @Override
    protected void configure(AuthenticationManagerBuilder auth) throws Exception {
        auth.userDetailsService(userService);
    }

    //...省略
}
```

