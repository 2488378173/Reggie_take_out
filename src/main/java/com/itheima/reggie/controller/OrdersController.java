package com.itheima.reggie.controller;


import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.itheima.reggie.common.BaseContext;
import com.itheima.reggie.common.R;
import com.itheima.reggie.dto.OrdersDto;
import com.itheima.reggie.entity.*;
import com.itheima.reggie.service.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/order")
public class OrdersController {

    @Autowired
    private DishService dishService;

    @Autowired
    private OrdersService ordersService;

    @Autowired
    private SetmealService setmealService;

    @Autowired
    private OrderDetailService orderDetailService;

    @Autowired
    private AddressBookService addressBookService;

    @Autowired
    private ShoppingCartService shoppingCartService;


    /**
     * 下单
     *
     * @param orders
     * @return
     */
    @PostMapping("/submit")
    public R<String> submit(@RequestBody Orders orders) {
        log.info("订单数据：{}", orders);
        ordersService.submit(orders);
        return R.success("下单成功");
    }


    /**
     * 订单展示
     *
     * @param page
     * @param pageSize
     * @return
     */
    @GetMapping("/userPage")
    public R<Page> userPage(int page, int pageSize) {
        log.info("page = {},pageSize = {}", page, pageSize);

        Page<Orders> pageInfo = new Page(page, pageSize);

        LambdaQueryWrapper<Orders> lambdaQueryWrapper = new LambdaQueryWrapper<>();

        lambdaQueryWrapper.orderByDesc(Orders::getOrderTime);

        ordersService.page(pageInfo);

        return R.success(pageInfo);
    }


    /**
     * 管理端
     * 分页查询
     *
     * @param page
     * @param pageSize
     * @param number
     * @param beginTime
     * @param endTime
     * @return
     */
    @GetMapping("/page")
    public R<Page> page(int page,
                        int pageSize,
                        String number,
                        @DateTimeFormat(pattern = "yyyy-mm-dd HH:mm:ss") Date beginTime,
                        @DateTimeFormat(pattern = "yyyy-mm-dd HH:mm:ss") Date endTime) {
        log.info("page = {},pageSize = {},number = {},beginTime = {},endTime = {}", page, pageSize, number, beginTime, endTime);
        Page<Orders> pageInfo = new Page(page, pageSize);
        Page<OrdersDto> ordersDtoPage = new Page<>();
        LambdaQueryWrapper<Orders> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        lambdaQueryWrapper.like(number != null, Orders::getNumber, number);
        if (beginTime != null) {
            lambdaQueryWrapper.between(Orders::getOrderTime, beginTime, endTime);
        }
        ordersService.page(pageInfo, lambdaQueryWrapper);
        BeanUtils.copyProperties(pageInfo, ordersDtoPage);
        List<Orders> records = pageInfo.getRecords();
        ordersDtoPage.setRecords(records.stream().map((item) -> {
            OrdersDto ordersDto = new OrdersDto();
            BeanUtils.copyProperties(item, ordersDto);
            Long userId = item.getAddressBookId();
            AddressBook addressBook = addressBookService.getById(userId);
            if (addressBook != null) {
                ordersDto.setUserName(addressBook.getConsignee());
            }
            return ordersDto;
        }).collect(Collectors.toList()));
        return R.success(ordersDtoPage);
    }


    /**
     * 操作订单状态
     *
     * @param orders
     * @return
     */
    @PutMapping
    public R<String> updateStatus(@RequestBody Orders orders) {
        log.info(orders.toString());
        ordersService.updateById(orders);
        return R.success("操作成功");
    }


    /**
     * 再来一单
     *
     * @param orders
     * @return
     */
    @PostMapping("/again")
    public R<String> again(@RequestBody Orders orders) {
        Long currentId = BaseContext.getThreadLocal();//用户id
        Long ordersId = orders.getId();//订单id
        LambdaQueryWrapper<Orders> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        lambdaQueryWrapper.eq(Orders::getId, ordersId);
        //根据订单id得到订单数据
        Orders one = ordersService.getOne(lambdaQueryWrapper);
        //订单表中的number，即订单明细表中order_id
        String number = one.getNumber();
        LambdaQueryWrapper<OrderDetail> orderDetailLambdaQueryWrapper = new LambdaQueryWrapper<>();
        orderDetailLambdaQueryWrapper.eq(OrderDetail::getOrderId, number);
        //根据order_id得到订单明细
        List<OrderDetail> orderDetails = orderDetailService.list(orderDetailLambdaQueryWrapper);
        List<ShoppingCart> shoppingCarts = new ArrayList<>(); //购物车
        //把原的购物车清空
        LambdaQueryWrapper<ShoppingCart> shoppingCartLambdaQueryWrapper = new LambdaQueryWrapper<>();
        shoppingCartLambdaQueryWrapper.eq(ShoppingCart::getUserId, BaseContext.getThreadLocal());
        shoppingCartService.remove(shoppingCartLambdaQueryWrapper);
        //遍历订单明细
        //将集合中数据假如购物车
        for (OrderDetail orderDetail : orderDetails) {
            ShoppingCart shoppingCart = new ShoppingCart();
            Long dishId = orderDetail.getDishId();//菜品id
            Long setmealId = orderDetail.getSetmealId();//套餐id
            //添加购物车部分数据
            shoppingCart.setUserId(currentId);
            shoppingCart.setDishFlavor(orderDetail.getDishFlavor());
            shoppingCart.setNumber(orderDetail.getNumber());
            shoppingCart.setAmount(orderDetail.getAmount());
            shoppingCart.setCreateTime(LocalDateTime.now());
            if (dishId != null) {
                //获取菜品
                LambdaQueryWrapper<Dish> dishLambdaQueryWrapper = new LambdaQueryWrapper<>();
                dishLambdaQueryWrapper.eq(Dish::getId, dishId);
                Dish dishOne = dishService.getOne(dishLambdaQueryWrapper);
                //添加到购物车
                shoppingCart.setDishId(dishId);
                shoppingCart.setName(dishOne.getName());
                shoppingCart.setImage(dishOne.getImage());
                shoppingCarts.add(shoppingCart);
            } else if (setmealId != null) {
                //同上-添加套餐
                LambdaQueryWrapper<Setmeal> setmealLambdaQueryWrapper = new LambdaQueryWrapper<>();
                setmealLambdaQueryWrapper.eq(Setmeal::getId, setmealId);
                Setmeal serviceOne = setmealService.getOne(setmealLambdaQueryWrapper);
                shoppingCart.setSetmealId(setmealId);
                shoppingCart.setName(serviceOne.getName());
                shoppingCart.setImage(serviceOne.getImage());
                shoppingCarts.add(shoppingCart);
            }
        }
        shoppingCartService.saveBatch(shoppingCarts);
        return R.success("操作成功");
    }
}
