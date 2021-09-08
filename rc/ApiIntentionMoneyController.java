package com.seewell.leasing.order.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.seewell.leasing.apartment.feign.ApmInfoService;
import com.seewell.leasing.apartment.model.ApmInfoDO;
import com.seewell.leasing.common.annotation.LoginUser;
import com.seewell.leasing.common.constant.Constants;
import com.seewell.leasing.common.dto.UserDTO;
import com.seewell.leasing.common.feign.SerialNumService;
import com.seewell.leasing.common.model.Result;
import com.seewell.leasing.common.model.SerialTypeEnum;
import com.seewell.leasing.common.utils.CopyBeanUtil;
import com.seewell.leasing.common.utils.JsonUtil;
import com.seewell.leasing.finance.constant.FuncEnum;
import com.seewell.leasing.finance.model.BillBalanceDTO;
import com.seewell.leasing.member.model.UniMemberVO;
import com.seewell.leasing.order.constants.OrderConstants;
import com.seewell.leasing.order.constants.PaymentConstants;
import com.seewell.leasing.order.model.*;
import com.seewell.leasing.order.model.pojo.PaymentInfoVO;
import com.seewell.leasing.order.service.*;
import com.seewell.leasing.order.utils.BillBalanceUtil;
import com.seewell.leasing.order.utils.OrderRedisUtil;
import com.seewell.leasing.order.utils.PushFinanceServiceUtil;
import com.seewell.leasing.order.utils.UserUtil;
import com.seewell.leasing.pay.feign.PayService;
import com.seewell.leasing.pay.model.EntityinfoVO;
import com.seewell.leasing.pay.model.PayChannelVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.*;

/**
 * <p>
 * 意向金活动、意向金订单表 前端控制器
 * </p>
 *
 * @author liuzhuo
 * @since 2021-06-28
 */

@Slf4j
@Api(tags = "意向金活动、意向金订单表2 ")
@RestController
@RequestMapping("/order/intention/money")
public class ApiIntentionMoneyController {
    @Autowired
    private IIntentionMoneyActivitiesService activitiesService;
    @Autowired
    private IIntentionMoneyOrderService orderService;
    @Autowired
    private SerialNumService serialNumService;
    @Autowired
    private PayService payService;
    @Autowired
    private ApmInfoService apmInfoService;
    @Autowired
    private IRefundService refundService;
    @Autowired
    UserUtil userUtil;
    @Autowired
    PushFinanceServiceUtil pushFinanceServiceUtil;
    @Autowired
    BillBalanceUtil billBalanceUtil;
    @Autowired
    OrderRedisUtil orderRedisUtil;

    /**
     * 查询意向金活动列表
     */
    @ApiOperation(value = "查询意向金活动列表")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "apartId", value = "公寓id", required = false, dataType = "String"),
            @ApiImplicitParam(name = "status", value = "状态：0未开始；1进行中；2已结束", required = false, dataType = "String")
    })
    @GetMapping("/activitiesList")
    public Result<List<IntentionMoneyActivitiesVO>> activitiesList(IntentionMoneyActivitiesQuery params) {
        LambdaQueryWrapper<IntentionMoneyActivitiesDO> queryWrapper = new LambdaQueryWrapper();
        if (StringUtils.isNotBlank(params.getApartId())){
            queryWrapper.eq(IntentionMoneyActivitiesDO::getApartId,params.getApartId());
        }
        if (StringUtils.isBlank(params.getStatus())){
            queryWrapper.eq(IntentionMoneyActivitiesDO::getStatus,"1");
        }else {
            queryWrapper.eq(IntentionMoneyActivitiesDO::getStatus,params.getStatus());
        }
        List<IntentionMoneyActivitiesDO> list = activitiesService.list(queryWrapper);
        return Result.succeed(CopyBeanUtil.copyList(list, IntentionMoneyActivitiesVO.class),"查询意向金活动列表成功！");
    }

    /**
     * 查询意向金订单列表
     */
    @ApiOperation(value = "查询意向金订单列表")
    @GetMapping("/orderList")
    public Result<List<IntentionMoneyOrderVO>> orderList(@LoginUser UserDTO userDTO) {
        LambdaQueryWrapper<IntentionMoneyOrderDO> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(IntentionMoneyOrderDO::getMemberId,userDTO.getId());
        queryWrapper.in(IntentionMoneyOrderDO::getStatus,"1","3");
        queryWrapper.orderByDesc(IntentionMoneyOrderDO::getCreateTime);
        List<IntentionMoneyOrderDO> list = orderService.list(queryWrapper);
        List<IntentionMoneyOrderVO> voList = CopyBeanUtil.copyList(list, IntentionMoneyOrderVO.class);
        return Result.succeed(getAdditionalParameters(voList),"查询意向金订单列表成功！");
    }

    /**
     * 查询意向金历史记录订单列表
     */
    @ApiOperation(value = "查询意向金历史记录订单列表")
    @GetMapping("/orderHistoryList")
    public Result<List<IntentionMoneyOrderVO>> orderHistoryList(@LoginUser UserDTO userDTO) {
        LambdaQueryWrapper<IntentionMoneyOrderDO> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(IntentionMoneyOrderDO::getMemberId,userDTO.getId());
        queryWrapper.in(IntentionMoneyOrderDO::getStatus,"2","4");
        queryWrapper.orderByDesc(IntentionMoneyOrderDO::getCreateTime);
        List<IntentionMoneyOrderDO> list = orderService.list(queryWrapper);
        List<IntentionMoneyOrderVO> voList = CopyBeanUtil.copyList(list, IntentionMoneyOrderVO.class);
        return Result.succeed(getAdditionalParameters(voList),"查询意向金历史记录订单列表成功！");
    }

    /**
     * 判断是否已参与过本次意向金活动
     */
    @ApiOperation(value = "判断是否已参与过本次意向金活动")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "activityId", value = "活动id", required = true, dataType = "String")
    })
    @PostMapping("/whetherAlreadyExistence")
    public Result whetherAlreadyExistence(@RequestBody IntentionMoneyOrderQuery query, @LoginUser UserDTO userDTO) {
        LambdaQueryWrapper<IntentionMoneyOrderDO> queryWrapper = new LambdaQueryWrapper();
        queryWrapper.eq(IntentionMoneyOrderDO::getActivityId,query.getActivityId());
        queryWrapper.eq(IntentionMoneyOrderDO::getMemberId,userDTO.getId());
        queryWrapper.in(IntentionMoneyOrderDO::getStatus,"1","2");
        List<IntentionMoneyOrderDO> list = orderService.list(queryWrapper);
        if (CollectionUtils.isNotEmpty(list)){
            return Result.failed("感谢您的参与！");
        }
        return Result.succeed("本次尚未参与！");
    }

    /**
     * 意向金订单保存
     */
    @ApiOperation(value = "意向金订单保存")
    @PostMapping("/addIntentionMoneyOrder")
    public Result addIntentionMoneyOrder(@RequestBody IntentionMoneyOrderDTO dto,@LoginUser UserDTO userDTO) {
        //意向金订单保存条件判断
        if (StringUtils.isBlank(dto.getActivityId())){
            return Result.failed("参数请求错误，意向金活动id为空！");
        }
        IntentionMoneyActivitiesVO byId = activitiesService.getById(dto.getActivityId());
        if (byId==null){
            return Result.failed("未找到对应的意向金活动信息！");
        }
        if ("0".equals(byId)){
            return Result.failed("意向金活动尚未开始，请活动开始后操作！");
        }
        if ("2".equals(byId)){
            return Result.failed("很抱歉，本次意向金活动已结束！");
        }
        //多次订单提交
        Boolean aBoolean = orderRedisUtil.lockSaveIntention(dto.getActivityId()+"_"+userDTO.getId());
        if (!aBoolean) {
            return Result.failed("请勿频繁操作,请稍后重试!");
        }
        //意向金订单保存
        String serialNo = serialNumService.generateSerialNumber(SerialTypeEnum.IntentionMoneyOrder.getName()).getData();
        //
        Result<UniMemberVO> uniMemberVOByMemberId = userUtil.findUniMemberVOByMemberId(userDTO.getId());
        if (uniMemberVOByMemberId==null || uniMemberVOByMemberId.getData()==null){
            return Result.failed("获取不到当前的会员信息！");
        }
        dto.setIntentionNo(serialNo); //IN+年月日+八位数  例：IN2021062977778888
        dto.setMemberId(userDTO.getId());
        dto.setMemberName(uniMemberVOByMemberId.getData().getName());
        dto.setMemberPhone(userDTO.getMobile());
        dto.setGroupId(byId.getGroupId());
        dto.setApartId(byId.getApartId());
        dto.setApartName(byId.getApartName());
        dto.setPaymentAmount(byId.getPaymentAmount());
        dto.setExpandCoefficient(byId.getExpandCoefficient());
        dto.setStatus("0");
        dto.setCreateUser(userDTO.getId());
        dto.setCreateTime(new Date());
        orderService.save(dto);
        return Result.succeed(dto,"保存成功");
    }

    @ApiOperation(value = "租客获取意向金订单的支付信息：金额、支付渠道等")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "intentionId", value = "意向金订单id", required = true, dataType = "String")
    })
    @GetMapping(value = "/findByIntentionId")
    public Result<Map<String,Object>> findByIntentionId(@RequestParam("intentionId") String intentionId, @LoginUser UserDTO userDTO){
        Map<String,Object> map = new HashMap<>();
        try {
            IntentionMoneyOrderVO byId = orderService.getById(intentionId);

            //支付金额
            PaymentInfoVO pvo = new PaymentInfoVO();
            pvo.setAmount(byId.getPaymentAmount());
            pvo.setTotalAmount(byId.getPaymentAmount());

            //查询公寓的财务公司id
            ApmInfoDO apmInfo = apmInfoService.findById(byId.getApartId());
            //查询财务公司下的生效的支付渠道
            EntityinfoVO entityInfo = new EntityinfoVO();
            entityInfo.setEntityId(apmInfo.getFnCompanyId());
            entityInfo.setEntityType("finance_company");
            entityInfo.setStatus("1");
            Result<List<PayChannelVO>> payChanelList = payService.entityInfo(entityInfo);
            map.put("payChannel",payChanelList.getData());
            map.put("paymentInfo",pvo);

        } catch (Exception e) {
            log.error("租客获取意向金订单的支付信息失败！",e);
            Result.failed("租客获取意向金订单的支付信息失败！");
        }
        return Result.succeed(map);
    }

    @ApiOperation(value = "意向金订单下单接口")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "mchId", value = "商户ID", required = true, dataType = "String"),
            @ApiImplicitParam(name = "channelId", value = "支付渠道ID", required = true, dataType = "String"),
            @ApiImplicitParam(name = "intentionId", value = "意向金订单id", required = true, dataType = "String")
    })
    @PostMapping(value = "/payIntentionMoneyOrder")
    public Result payIntentionMoneyOrder(@RequestBody PaymentInfoVO pVo, @LoginUser UserDTO userDTO){
        log.info("pVo=="+ JsonUtil.toJSONString(pVo));
        return orderService.payIntentionMoneyOrder(pVo,userDTO);
    }

    @ApiOperation(value = "意向金订单退款接口")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "mchId", value = "商户ID", required = true, dataType = "String"),
            @ApiImplicitParam(name = "channelId", value = "支付渠道ID", required = true, dataType = "String"),
            @ApiImplicitParam(name = "intentionId", value = "意向金订单id", required = true, dataType = "String")
    })
    @PostMapping(value = "/refundIntentionMoneyOrder")
    public Result refundIntentionMoneyOrder(@RequestBody IntentionMoneyOrderRefundVO vo){

        //多次退款提交
        Boolean aBoolean = orderRedisUtil.lockRefundIntention(vo.getIntentionId());
        if (!aBoolean) {
            return Result.failed("请勿频繁操作,请稍后重试!");
        }
        IntentionMoneyOrderVO byId = orderService.getById(vo.getIntentionId());

        RefundDO refundDO = new RefundDO();
        Date date = new Date();
        Result<String> serialResult = serialNumService.generateSerialNumber(SerialTypeEnum.RefundNo.getName());
        refundDO.setBatchNo(serialResult.getData());
        refundDO.setMemberId(byId.getMemberId());
        refundDO.setReservationId(byId.getIntentionId());
        refundDO.setReservationNo(byId.getIntentionNo());
        refundDO.setMemberName(byId.getMemberName());
        refundDO.setMemberMobile(byId.getMemberPhone());
        refundDO.setRefundType(OrderConstants.REFUND_TYPE_YXJ);
        refundDO.setRefundAmount(byId.getPaymentAmount());
        refundDO.setOperatorType(OrderConstants.TERMINATE_TYPE_MEMBER);
        refundDO.setOpUserId(byId.getMemberId());
        refundDO.setOpUserName(byId.getMemberName());
        refundDO.setApartmentId(byId.getApartId());
        refundDO.setCompanyId(byId.getGroupId());
        refundDO.setStatus(OrderConstants.REFUND_STATUS_WAIT);
        refundDO.setDelFlag(Constants.DELETE_FLAG_FALSE);
        refundDO.setCreateTime(date);
        refundDO.setCreateUser(byId.getMemberId());
        refundDO.setUpdateTime(date);
        refundDO.setUpdateUser(byId.getMemberId());
        refundDO.setBankLocationCode(vo.getRefundBranch());
        refundDO.setReceiverName(vo.getRefundPersonName());
        refundDO.setReceiverAccountNo(vo.getRefundCardNo());
        refundDO.setReceiverAccountType("03");
        refundDO.setBankName(vo.getRefundBank());
        refundDO.setBankBranchName(vo.getRefundBranch());
        refundDO.setAuditStatus(OrderConstants.AUDIT_STATUS_WAIT);
        refundService.save(refundDO);

        byId.setStatus("6");
        byId.setUpdateTime(date);
        byId.setUpdateUser(byId.getMemberId());
        orderService.updateById(byId);

        return Result.succeed(refundDO,"系统处理后，资金将会退回到您的尾号"
                +vo.getRefundCardNo().substring(vo.getRefundCardNo().length()-4)
                +vo.getRefundBank()+"账户！");
    }

    //意向金订单列表、历史列表提供额外参数
    private List<IntentionMoneyOrderVO> getAdditionalParameters(List<IntentionMoneyOrderVO> list){
        list.stream().forEach(vo->{
            ApmInfoDO byId = apmInfoService.findById(vo.getApartId());
            vo.setApartAddress(byId.getAddress());
            vo.setStewardTel(byId.getStewardTel());
        });
        return list;
    }
}
