package com.itheima.reggie.dto;

import com.itheima.reggie.entity.Setmeal;
import com.itheima.reggie.entity.SetmealDish;
import lombok.Data;

import java.util.List;

@Data
public class SetmealDto extends Setmeal {

    //套餐关联的菜品集合
    private List<SetmealDish> setmealDishes;

    //分类名称
    private String categoryName;
}
