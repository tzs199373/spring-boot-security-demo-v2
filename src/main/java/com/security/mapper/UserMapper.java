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
