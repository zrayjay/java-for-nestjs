package dev.javafornestjs.data;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface TaskMapper extends BaseMapper<TaskRow> {
    List<TaskRow> listLimited(@Param("limit") int limit);
    String ownerName(@Param("id") int id);
    List<OwnedTask> joinedOwners();
    record OwnedTask(int id, String title, String owner) { }
}
