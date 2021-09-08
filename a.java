package com.seewell.leasing.order.controller;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.io.IoUtil;
import cn.hutool.poi.excel.ExcelUtil;
import cn.hutool.poi.excel.ExcelWriter;
import com.alibaba.fastjson.JSON;
import com.seewell.leasing.apartment.model.ApmInfoDO;
import com.seewell.leasing.common.annotation.LoginUser;
import com.seewell.leasing.common.dto.UserDTO;
import com.seewell.leasing.common.exception.BusinessException;
import com.seewell.leasing.order.constants.PaymentConstants;
import com.seewell.leasing.order.model.*;
import com.seewell.leasing.order.model.pojo.*;
import com.seewell.leasing.order.utils.ApartmentUtil;
import com.seewell.leasing.order.utils.DateUtil;
import com.seewell.leasing.order.utils.StringUtil;
import com.seewell.leasing.user.feign.UserService;
import org.springframework.web.bind.annotation.RequestMapping;

import org.springframework.web.bind.annotation.RestController;
import com.seewell.leasing.common.controller.SuperController;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import com.seewell.leasing.common.model.PageResult;
import com.seewell.leasing.common.model.Result;
import com.seewell.leasing.order.service.IRoomFeeService;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * <p>
 * 费用表 前端控制器
 * </p>
 *
 * @author zzx
 * @since 2021-03-27
 */
@Slf4j
@Api(tags = "费用表")
@RestController
@RequestMapping("/backendapi/roomFees")
public class ApmRoomFeeController extends SuperController {
    @Autowired
    private IRoomFeeService service;
    @Autowired
    private UserService userService;
    @Autowired
    private ApartmentUtil apartmentUtil;

    /**
     * 列表
     */
    @ApiOperation(value = "A.后台账单管理列表-支持翻页")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "pageNumber", value = "页号", required = true, dataType = "Integer"),
            @ApiImplicitParam(name = "pageSize", value = "每页记录条数", required = true, dataType = "Integer"),
            @ApiImplicitParam(name = "status", value = "标识:0待支付;1已支付;", required = true, dataType = "String"),
            @ApiImplicitParam(name = "delFlag", value = "删除标记：0未删除；1已删除", required = true, dataType = "String"),
            @ApiImplicitParam(name = "reservationId", value = "预订单ID", required = true, dataType = "String")
    })
    @RequestMapping(value = "/findList",method = RequestMethod.GET)
    public PageResult<RoomFeeVO> findList(RoomFeeQuery params,@LoginUser UserDTO loginUser) {
        try {
            return service.findList(params);
        } catch (Exception e) {
            log.error("台账管理列表查询失败",e);
            return PageResult.failed("台账管理列表查询失败");
        }
    }

    /**
     * 新建账单
     */
    @ApiOperation(value = "新建账单")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "reservationId", value = "预订单ID", required = true, dataType = "String"),
            @ApiImplicitParam(name = "roomFeeList", value = "账单费用列表", required = true, dataType = "List")
    })
    @RequestMapping(value = "/saveNewRoomFeeList",method = RequestMethod.POST)
    public Result saveNewRoomFeeList(@RequestBody CreateNewRoomFeeDTO dto,@LoginUser UserDTO user) {
        service.saveNewRoomFeeList(dto,user);
        return Result.succeed("保存成功");
    }

    /**
     * 作废账单
     */
    @ApiOperation(value = "作废账单")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "reservationId", value = "预订单ID", required = true, dataType = "String"),
            @ApiImplicitParam(name = "roomFeeList", value = "账单费用列表", required = true, dataType = "List")
    })
    @RequestMapping(value = "/invalidRoomFee",method = RequestMethod.POST)
    public Result invalidRoomFee(@RequestBody InvalidRoomFeeVO vo, @LoginUser UserDTO user) {

        if (vo.getUserName() == null || "".equals(vo.getUserName())) {
            return Result.failed("userName is null");
        }
        if(vo.getPassword() == null || "".equals(vo.getPassword())){
            return Result.failed("password is null");
        }

        Result userLogin = userService.verificationPassword(vo.getUserName(), vo.getPassword());
        if(Result.isSuccessed(userLogin)){
            Result result = service.invalidRoomFee(vo, user);
            return result;
        } else {
            return userLogin;
        }

    }

    /**
     * 查询应付账单
     */
    @ApiOperation(value = "查询应付账单")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "reservationId", value = "预订单ID", required = true, dataType = "String")
    })
    @RequestMapping(value = "/selectAccountPayable",method = RequestMethod.GET)
    public Result<Map<String,Object>> selectAccountPayable(@RequestParam("reservationId") String reservationId , @LoginUser UserDTO user) {
        try {
            Map<String,Object> map = new HashMap<>();
            List<AccountPayableVo> accountPayableVos = service.selectAccountPayable(reservationId, user);
            BigDecimal totalRoomFee = new BigDecimal(0);
            BigDecimal totalServiceFee = new BigDecimal(0);
            if (accountPayableVos!=null && accountPayableVos.size()>0) {
                for (AccountPayableVo payableVo : accountPayableVos) {
                    totalRoomFee = totalRoomFee.add(payableVo.getRoomFee());
                    totalServiceFee = totalServiceFee.add(payableVo.getServiceFee());
                }
                map.put("total",accountPayableVos.size());
            }
            map.put("rows", accountPayableVos);
            map.put("totalRoomFee", totalRoomFee);
            map.put("totalServiceFee", totalServiceFee);
            return Result.succeed(map);
        } catch (Exception e) {
           log.error("",e);
            return Result.failed("查询失败");
        }

    }

    @ApiOperation(value = "水电账单")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "reservationId", value = "预订单ID", required = true, dataType = "String"),
            @ApiImplicitParam(name = "roomFeeList", value = "账单费用列表", required = true, dataType = "List")
    })
    @RequestMapping(value = "/saveEnergyFeeList",method = RequestMethod.POST)
    public Result saveEnergyFeeList(@RequestBody CreateNewRoomFeeDTO dto) {
        List<RoomUnitFeeDTO> roomFeeList = dto.getRoomFeeList();
        if(roomFeeList == null || roomFeeList.size() == 0){
            return Result.failed("roomFeeList不能为空");
        }
        if(dto.getReservationId() == null || "".equals(dto.getReservationId())){
            return Result.failed("reservationId不能为空");
        }

        for(int i = 0 ; i < roomFeeList.size(); i++){
            RoomUnitFeeDTO feeVo = roomFeeList.get(i);
            if(feeVo.getNum() == null){
                return Result.failed("num不能为空");
            }
            if(feeVo.getNum().compareTo(BigDecimal.ZERO) <=0){
                return Result.failed("num不能小于等于0");
            }
            if(feeVo.getFeeType() == null){
                return Result.failed("feeType不能为空");
            }
            if(feeVo.getFeeValue() == null){
                return Result.failed("feeValue不能为空");
            }
            if(feeVo.getStartTime() == null){
                return Result.failed("startTime，账单开始时间不能为空");
            }
            if(feeVo.getEndTime() == null){
                return Result.failed("endTime，账单结束时间不能为空");
            }
        }
        service.saveEnergyFeeList(dto);
        return Result.succeed("保存成功");
    }

    @ApiOperation(value = "账单明细")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "apartmentIds", value = "公寓Id(多个公寓id用逗号分割开)", required = false, dataType = "String"),
            @ApiImplicitParam(name = "roomNo", value = "房间号", required = false, dataType = "String"),
            @ApiImplicitParam(name = "bookerName", value = "租客信息", required = false, dataType = "String"),
            @ApiImplicitParam(name = "startDate", value = "开始日期", required = false, dataType = "String"),
            @ApiImplicitParam(name = "endDate", value = "结束日期", required = false, dataType = "String"),
            @ApiImplicitParam(name = "status", value = "账单状态(null:全部，0:未收款，1:已收款)", required = false, dataType = "String")
    })
    @GetMapping("/getBillPayReport")
    public PageResult<BillReportVO> getBillPayReport(BillReportQuery billReportQuery){
        log.info("call getBillPayReport:({})", JSON.toJSONString(billReportQuery));
        try {
            //获取当前用户的公寓
            if(StringUtil.isEmpty(billReportQuery.getApartmentIds())){
                Result<List<ApmInfoDO>> userApartmentIds = apartmentUtil.findApmInfosByUserIds();
                if(Result.isFailed(userApartmentIds)){
                    return PageResult.failed(String.format("获取当前用户具有的公寓权限失败:%s", userApartmentIds.getMessage()));
                }
                if(CollectionUtil.isEmpty(userApartmentIds.getData())){
                    return PageResult.failed("获取账单明细失败,当前用户没有任何公寓的权限");
                }
                String apartmentIds = userApartmentIds.getData().stream().map(item -> {
                    return item.getId();
                }).collect(Collectors.joining(","));
                billReportQuery.setApartmentIds(apartmentIds);
            }
            List<BillReportVO> billReportVOS = service.getBillPayReport(billReportQuery, false);
            Long count = service.getBillPayReportCount(billReportQuery);
            return PageResult.succeed(billReportVOS, count);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return PageResult.failed(String.format("获取账单明细错误，%s", e));
        }
    }

    @ApiOperation(value = "账单明细导出")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "apartmentIds", value = "公寓Id(多个公寓id用逗号分割开)", required = false, dataType = "String"),
            @ApiImplicitParam(name = "roomNo", value = "房间号", required = false, dataType = "String"),
            @ApiImplicitParam(name = "bookerName", value = "租客信息", required = false, dataType = "String"),
            @ApiImplicitParam(name = "startDate", value = "开始日期", required = false, dataType = "String"),
            @ApiImplicitParam(name = "endDate", value = "结束日期", required = false, dataType = "String"),
            @ApiImplicitParam(name = "status", value = "账单状态(null:全部，0:未收款，1:已收款)", required = false, dataType = "String")
    })
    @GetMapping("/exportBillPayReport")
    public void exportBillPayReport(BillReportQuery billReportQuery, HttpServletResponse response){
        log.info("call exportBillPayReport:({})", JSON.toJSONString(billReportQuery));
        ExcelWriter writer = ExcelUtil.getWriter();
        ServletOutputStream out = null;
        try {
            if(StringUtil.isEmpty(billReportQuery.getApartmentIds())){
                Result<List<ApmInfoDO>> userApartmentIds = apartmentUtil.findApmInfosByUserIds();
                if(Result.isFailed(userApartmentIds)){
                    throw new BusinessException(String.format("获取当前用户具有的公寓权限失败:%s", userApartmentIds.getMessage()));
                }
                if(CollectionUtil.isEmpty(userApartmentIds.getData())){
                    throw new BusinessException("获取账单明细失败,当前用户没有任何公寓的权限");
                }
                String apartmentIds = userApartmentIds.getData().stream().map(item -> {
                    return item.getId();
                }).collect(Collectors.joining(","));
                billReportQuery.setApartmentIds(apartmentIds);
            }
            List<BillReportVO> billReportVOS = service.getBillPayReport(billReportQuery, true);
            for (BillReportVO item : billReportVOS) {
                if(PaymentConstants.PayStatus.PAYING.type.equals(item.getStatus())){
                    item.setStatus("待收款");
                } else if(PaymentConstants.PayStatus.PAYED.type.equals(item.getStatus())){
                    item.setStatus("已收款");
                }
            }
            long start3 = System.currentTimeMillis();
            Map<String, String> fieldAndAlias = new LinkedHashMap<>();
            fieldAndAlias.put("createTimeStr", "创建日期");
            fieldAndAlias.put("roomNo", "房间");
            fieldAndAlias.put("roomTypeName", "房型");
            fieldAndAlias.put("bookerName", "租客姓名");
            fieldAndAlias.put("bookerPhone", "租客手机");
            fieldAndAlias.put("rentDate", "约定租期");
            fieldAndAlias.put("paymentTypeName", "交租方式");
            fieldAndAlias.put("contactNo", "合同编号");
            fieldAndAlias.put("reservationNo", "订单编号");
            fieldAndAlias.put("receptionNo", "接待单号");
            fieldAndAlias.put("roomFeeNo", "账单号");
            fieldAndAlias.put("name", "营业项目");
            fieldAndAlias.put("billDate", "账单日期");
            fieldAndAlias.put("finalTimeStr", "最晚缴费");
            fieldAndAlias.put("totalAmount", "应缴费用");
            fieldAndAlias.put("discountAmount", "优惠金额");
            fieldAndAlias.put("receiveAmount", "已收款");
            fieldAndAlias.put("amount", "未付金额");
            fieldAndAlias.put("remark", "备注");
            fieldAndAlias.put("status", "状态");
            writer.setHeaderAlias(fieldAndAlias);
            //只导出设置的标题
            writer.setOnlyAlias(true);
            writer.setColumnWidth(0, 20).setColumnWidth(5, 25)
                    .setColumnWidth(11, 25).setColumnWidth(2, 20)
                    .setColumnWidth(4, 15).setColumnWidth(7, 20)
                    .setColumnWidth(8, 20).setColumnWidth(9,20)
                    .setColumnWidth(12, 15);
            writer.write(billReportVOS, true);

            response.setContentType("application/vnd.ms-excel;charset=utf-8");
            response.setHeader("Content-Disposition", "attachment;filename=payable"+ DateUtil.format(new Date(), "yyyyMMddHHmm") +".xls");
            out = response.getOutputStream();
            writer.flush(out, true);
            System.out.println("****************3***************");
            long end3 = System.currentTimeMillis();
            System.out.println(end3-start3);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        } finally {
            IoUtil.close(out);
            writer.close();
        }
    }

    /**
     * 待办中心：待催单
     *
     * @return
     */
    @GetMapping(value = "/searchReservationArrearsFeeList")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "pageNumber", value = "页号", required = true, dataType = "Integer"),
            @ApiImplicitParam(name = "pageSize", value = "每页记录条数", required = true, dataType = "Integer"),
            @ApiImplicitParam(name = "apartmentId", value = "公寓ID", required = true, dataType = "String"),
            @ApiImplicitParam(name = "elseFeeFlag", value = "待办中心标记：1、催租 2、催其他费用", required = true, dataType = "String"),
    })
    public PageResult<RoomFeeVO> searchReservationArrearsFeeList(RoomFeeQuery roomFeeQuery) {
        return service.searchReservationArrearsFeeList(roomFeeQuery);
    }

    /**
     * 待办中心：待催单详情
     *
     * @return
     */
    @PostMapping(value = "/searchFeeListDetail")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "reservationId", value = "预订单ID", required = true, dataType = "String"),
            @ApiImplicitParam(name = "elseFeeFlag", value = "待办中心标记：1、催租 2、催其他费用", required = true, dataType = "String"),
    })
    public Result<List<RoomFeeVO>> searchFeeListDetail(@RequestBody RoomFeeQuery roomFeeQuery) {
        return service.findRoomFeeListDetail(roomFeeQuery);
    }

    /**
     * 催账提醒
     *
     * @return
     */
    @PostMapping(value = "/gatheringRemind")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "reservationId", value = "预订单ID", required = true, dataType = "String"),
    })
    public Result gatheringRemind(@RequestBody RoomFeeQuery query) {
        return service.gatheringRemind(query);
    }

    /**
     * 批量---催款房租服务费
     *
     * @return
     */
    @PostMapping(value = "/roomFeeRemindList")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "reservationId", value = "预订单ID", required = true, dataType = "String"),
            @ApiImplicitParam(name = "elseFeeFlag", value = "待办中心标记：1、催租 2、催其他费用", required = true, dataType = "String"),
            @ApiImplicitParam(name = "finalTime", value = "账单最晚缴费时间", required = true, dataType = "date"),
    })
    public Result roomFeeRemindList(@RequestBody List<RoomFeeQuery> queryList) {
        try {
            for (RoomFeeQuery query:queryList) {
                service.roomFeeRemind(query);
            }
        }catch (Exception e){
            return Result.succeed("短信发送异常，批量催账失败");
        }
        return Result.succeed("批量催账成功");
    }

    /**
     * 批量催款--其他费用
     *
     * @return
     */
    @PostMapping(value = "/roomFeeRemindElseList")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "reservationId", value = "预订单ID", required = true, dataType = "String"),
            @ApiImplicitParam(name = "elseFeeFlag", value = "待办中心标记：1、催租 2、催其他费用", required = true, dataType = "String"),
            @ApiImplicitParam(name = "finalTime", value = "账单最晚缴费时间", required = true, dataType = "date"),

    })
    public Result roomFeeRemindElseList(@RequestBody List<RoomFeeQuery> queryList) {
        try {
            for (RoomFeeQuery query:queryList) {
                service.roomFeeRemindElse(query);
            }
        }catch (Exception e){
            return Result.succeed("短信发送异常，批量催账失败");
        }
        return Result.succeed("批量催账成功");
    }


    /***
     * 房费催租-----房费
     * @return
     */
    @PostMapping(value = "/roomFeeRemind")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "reservationId", value = "预订单ID", required = true, dataType = "String"),
            @ApiImplicitParam(name = "elseFeeFlag", value = "待办中心标记：1、催租 2、催其他费用", required = true, dataType = "String"),
            @ApiImplicitParam(name = "finalTime", value = "账单最晚缴费时间", required = true, dataType = "date"),
    })
    public Result roomFeeRemind(@RequestBody RoomFeeQuery query) {
        return service.roomFeeRemind(query);
    }

    /***
     * 除房租/服务费之外的其他费用
     * @return
     */
    @PostMapping(value = "/roomFeeRemindElse")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "reservationId", value = "预订单ID", required = true, dataType = "String"),
            @ApiImplicitParam(name = "elseFeeFlag", value = "待办中心标记：1、催租 2、催其他费用", required = true, dataType = "String"),
            @ApiImplicitParam(name = "finalTime", value = "账单最晚缴费时间", required = true, dataType = "date"),

    })
    public Result roomFeeRemindElse(@RequestBody RoomFeeQuery query) {
        return service.roomFeeRemindElse(query);
    }

    /***
     * 查询时间段内财务所需的日费用信息
     * @return
     */
    @PostMapping(value = "/getRoomDayFeeByDateTime")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "dateTime", value = "拉取日费用时间", required = true, dataType = "date"),
            @ApiImplicitParam(name = "apartId", value = "公寓ID", required = false, dataType = "string"),
    })
    public Result<List<RoomDayFeeVO>> getRoomDayFeeByDateTime(@RequestBody RoomDayFeeQuery query){
        try {
            return Result.succeed(service.getRoomDayFeeByDateTime(query));
        }catch (Exception e){
            log.error("拉取日费用信息失败: "+e);
            return Result.failed("拉取日费用信息失败: "+e);
        }
    }
}

