package com.njhtr.seekai.memory;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface UserMemoryRepository {

    // ========== UserMemory ==========
    int saveMemory(UserMemory memory);

    List<UserMemory> findByUserId(@Param("userId") String userId);

    List<UserMemory> findByUserIdAndType(@Param("userId") String userId, @Param("memoryType") String memoryType);

    int deleteMemory(@Param("id") Long id);

    int updateMemory(UserMemory memory);

    // ========== UserPreference ==========
    int savePreference(UserPreference preference);

    List<UserPreference> findPreferencesByUserId(@Param("userId") String userId);

    UserPreference findPreference(@Param("userId") String userId, @Param("preferenceKey") String preferenceKey);

    int updatePreference(UserPreference preference);

    // ========== UserHabit ==========
    int saveHabit(UserHabit habit);

    List<UserHabit> findHabitsByUserId(@Param("userId") String userId);

    UserHabit findHabit(@Param("userId") String userId, @Param("habitType") String habitType, @Param("habitKey") String habitKey);

    int incrementHabitFrequency(@Param("id") Long id);

    int updateHabit(UserHabit habit);

    // 获取用户高频习惯
    List<UserHabit> findFrequentHabits(@Param("userId") String userId, @Param("limit") int limit);
}