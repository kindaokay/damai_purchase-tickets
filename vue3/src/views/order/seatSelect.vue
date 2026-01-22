<template>
  <div class="seat-select-container">
    <Header></Header>
    <div class="main-content">
      <!-- 顶部时间和票价区域 -->
      <div class="top-bar">
        <div class="date-time">{{ seatData.showTime }} {{ seatData.showWeekTime }}</div>
        <div class="price-tags">
          <div 
            v-for="price in seatData.priceList" 
            :key="price" 
            class="price-tag"
            :class="{ active: selectedPrice === price }"
            @click="filterByPrice(price)"
          >
            <span class="color-dot" :style="{ backgroundColor: getPriceColor(price) }"></span>
            <span class="price-text" :class="{ 'active-price': selectedPrice === price }">{{ selectedPrice === price ? price + '元' : '¥' + price }}</span>
          </div>
          <div 
            class="price-tag"
            :class="{ active: selectedPrice === '' }"
            @click="filterByPrice('')"
          >
            <span class="price-text">全部</span>
          </div>
        </div>
      </div>

      <!-- 座位图区域 -->
      <div class="seat-area">
        <div class="venue-wrapper">
          <!-- 舞台提示 -->
          <div class="stage-box">
            <span>舞台</span>
          </div>
          <!-- 场馆轮廓 -->
          <div class="venue-outline">
            <div class="seat-map-container">
            <div 
              v-for="row in allRows" 
              :key="row.rowCode" 
              class="seat-row"
            >
              <span class="row-label">{{ row.rowCode }}排</span>
              <div class="seats">
                <div 
                  v-for="seat in row.seats" 
                  :key="seat.id"
                  class="seat"
                  :class="getSeatClass(seat)"
                  @click="toggleSeat(seat)"
                  :title="`${seat.rowCode}排${seat.colCode}座 ¥${seat.price}`"
                >
                  <span 
                    class="seat-dot"
                    :style="{ backgroundColor: getSeatColor(seat), opacity: getSeatOpacity(seat) }"
                  ></span>
                </div>
              </div>
              <span class="row-label">{{ row.rowCode }}排</span>
            </div>
          </div>
          </div>
        </div>
      </div>

      <!-- 底部栏 -->
      <div class="bottom-bar">
        <div class="left-section">
          <div class="price-display">
            <span class="currency">¥</span>
            <span class="amount">{{ totalPrice }}</span>
          </div>
          <div class="selected-seats" v-if="selectedSeats.length > 0">
            <span 
              v-for="seat in selectedSeats" 
              :key="seat.id" 
              class="seat-tag"
              @click="removeSeat(seat)"
            >
              {{ seat.rowCode }}排{{ seat.colCode }}座
              <i class="remove-icon">×</i>
            </span>
          </div>
        </div>
        <button 
          class="buy-btn"
          :class="{ disabled: selectedSeats.length === 0 }"
          :disabled="selectedSeats.length === 0"
          @click="submitOrder"
        >
          立即购买
        </button>
      </div>
    </div>
    <Footer></Footer>
  </div>
</template>

<script setup name="seatSelect">
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getSeatList } from '@/api/seatDetail'
import Header from '@/components/header/index'
import Footer from '@/components/footer/index'
import { ElMessage } from 'element-plus'

const route = useRoute()
const router = useRouter()

// 从路由state获取节目详情
const detailList = ref({})
const seatData = ref({
  programId: '',
  place: '',
  showTime: '',
  showWeekTime: '',
  priceList: [],
  seatVoMap: {}
})

// 选中的座位
const selectedSeats = ref([])
// 价格筛选
const selectedPrice = ref('')
// 最大可选座位数
const maxSeats = 6

// 价格颜色映射 - 大麦风格颜色
const priceColors = {
  0: '#8CD790',  // 淡绿色
  1: '#FFD966',  // 淡黄色
  2: '#F4A460',  // 淡橙色
  3: '#FF9EAA',  // 淡粉色
  4: '#87CEEB',  // 天蓝色
  5: '#DDA0DD',  // 梅红色
  6: '#B8D4E3',  // 淡蓝色
  7: '#F0E68C',  // 卡其色
  8: '#98D8C8',  // 薄荷绿
  9: '#F7CAC9'   // 玫瑰粉
}

// 根据价格获取颜色
const getPriceColor = (price) => {
  const index = seatData.value.priceList.indexOf(price.toString())
  return priceColors[index % 10] || '#ccc'
}

// 筛选后的座位图
const filteredSeatMap = computed(() => {
  if (selectedPrice.value === '') {
    return seatData.value.seatVoMap
  }
  const filtered = {}
  if (seatData.value.seatVoMap[selectedPrice.value]) {
    filtered[selectedPrice.value] = seatData.value.seatVoMap[selectedPrice.value]
  }
  return filtered
})

// 获取所有行的座位数据（始终显示所有座位，不筛选隐藏）
const allRows = computed(() => {
  const rowMap = {}
  // 始终显示所有票档的座位
  Object.values(seatData.value.seatVoMap).forEach(seats => {
    seats.forEach(seat => {
      if (!rowMap[seat.rowCode]) {
        rowMap[seat.rowCode] = {
          rowCode: seat.rowCode,
          seats: []
        }
      }
      rowMap[seat.rowCode].seats.push(seat)
    })
  })
  
  // 按列排序
  Object.values(rowMap).forEach(row => {
    row.seats.sort((a, b) => Number(a.colCode) - Number(b.colCode))
  })
  
  // 按行号排序返回
  return Object.values(rowMap).sort((a, b) => Number(a.rowCode) - Number(b.rowCode))
})

// 计算总价
const totalPrice = computed(() => {
  return selectedSeats.value.reduce((sum, seat) => sum + Number(seat.price), 0)
})

// 获取座位颜色
const getSeatColor = (seat) => {
  const isSelected = selectedSeats.value.some(s => s.id === seat.id)
  
  // 选中状态 - 粉红色
  if (isSelected) {
    return '#FF375D'
  }
  
  // 已售 - 浅灰色
  if (seat.sellStatus !== '1') {
    return '#E8E8E8'
  }
  
  // 未售 - 根据价格显示颜色
  return getPriceColor(seat.price)
}

// 获取座位透明度
const getSeatOpacity = (seat) => {
  // 已选中的座位始终不透明
  const isSelected = selectedSeats.value.some(s => s.id === seat.id)
  if (isSelected) {
    return 1
  }
  
  // 已售的座位始终不透明
  if (seat.sellStatus !== '1') {
    return 1
  }
  
  // 没有选择票档，所有未售座位都半透明
  if (selectedPrice.value === '') {
    return 0.4
  }
  
  // 选中票档的座位100%不透明，其他票档半透明
  return seat.price.toString() === selectedPrice.value ? 1 : 0.3
}

// 按行分组
const groupByRow = (seats) => {
  const rowMap = {}
  seats.forEach(seat => {
    if (!rowMap[seat.rowCode]) {
      rowMap[seat.rowCode] = {
        rowCode: seat.rowCode,
        seats: []
      }
    }
    rowMap[seat.rowCode].seats.push(seat)
  })
  // 按列排序
  Object.values(rowMap).forEach(row => {
    row.seats.sort((a, b) => Number(a.colCode) - Number(b.colCode))
  })
  // 按行号排序返回
  return Object.values(rowMap).sort((a, b) => Number(a.rowCode) - Number(b.rowCode))
}

// 获取座位样式类
const getSeatClass = (seat) => {
  const isSelected = selectedSeats.value.some(s => s.id === seat.id)
  return {
    'seat-available': seat.sellStatus === '1',
    'seat-sold': seat.sellStatus !== '1',
    'seat-selected': isSelected
  }
}

// 切换座位选中状态
const toggleSeat = (seat) => {
  // 已售出的座位不可选
  if (seat.sellStatus !== '1') {
    ElMessage.warning('该座位已售出')
    return
  }
  
  const index = selectedSeats.value.findIndex(s => s.id === seat.id)
  if (index > -1) {
    // 取消选中
    selectedSeats.value.splice(index, 1)
  } else {
    // 检查是否超过最大数量
    if (selectedSeats.value.length >= maxSeats) {
      ElMessage.warning(`每笔订单最多选择${maxSeats}个座位`)
      return
    }
    // 添加选中
    selectedSeats.value.push(seat)
  }
}

// 移除选中的座位
const removeSeat = (seat) => {
  const index = selectedSeats.value.findIndex(s => s.id === seat.id)
  if (index > -1) {
    selectedSeats.value.splice(index, 1)
  }
}

// 按价格筛选
const filterByPrice = (price) => {
  selectedPrice.value = price
}

// 返回上一页
const goBack = () => {
  router.back()
}

// 提交订单
const submitOrder = () => {
  if (selectedSeats.value.length === 0) {
    ElMessage.warning('请先选择座位')
    return
  }
  
  // 获取选中座位的ID列表
  const seatIdList = selectedSeats.value.map(seat => seat.id)
  // 获取票档ID（取第一个选中座位的票档ID）
  const ticketCategoryId = selectedSeats.value[0].ticketCategoryId
  
  // 跳转到订单页面，传递选座信息
  router.replace({
    path: '/order/index',
    state: {
      'detailList': JSON.stringify(detailList.value),
      'allPrice': totalPrice.value,
      'countPrice': selectedSeats.value[0].price,
      'num': selectedSeats.value.length,
      'ticketCategoryId': ticketCategoryId,
      'seatIdList': JSON.stringify(seatIdList),
      'isChooseSeat': true,
      'selectedSeats': JSON.stringify(selectedSeats.value)
    }
  })
}

// 获取座位数据
const fetchSeatData = async () => {
  try {
    const programId = detailList.value.id
    const response = await getSeatList({ programId })
    if (response.code === '0' && response.data) {
      seatData.value = response.data
    } else {
      ElMessage.error('获取座位信息失败')
    }
  } catch (error) {
    console.error('获取座位信息失败:', error)
    ElMessage.error('获取座位信息失败')
  }
}

onMounted(() => {
  // 从history.state获取节目详情
  if (history.state && history.state.detailList) {
    detailList.value = JSON.parse(history.state.detailList)
    fetchSeatData()
  } else {
    ElMessage.error('缺少节目信息')
    router.back()
  }
})
</script>

<style scoped lang="scss">
.seat-select-container {
  min-height: 100vh;
  background: #f5f5f5;
  
  .main-content {
    width: 100%;
    max-width: 1400px;
    margin: 0 auto;
    padding: 20px 20px 100px;
    
    // 顶部时间和票价区域
    .top-bar {
      background: #fff;
      padding: 15px 20px;
      margin-bottom: 15px;
      
      .date-time {
        font-size: 16px;
        color: #333;
        margin-bottom: 15px;
        font-weight: 500;
      }
      
      .price-tags {
        display: flex;
        flex-wrap: wrap;
        gap: 10px;
        
        .price-tag {
          display: inline-flex;
          align-items: center;
          padding: 6px 12px;
          border: 1px solid #e5e5e5;
          border-radius: 4px;
          cursor: pointer;
          transition: all 0.2s;
          background: #fff;
          
          &:hover {
            border-color: #FF375D;
          }
          
          &.active {
            border-color: #FF375D;
            background: #FFF5F7;
          }
          
          .color-dot {
            width: 12px;
            height: 12px;
            border-radius: 50%;
            margin-right: 6px;
          }
          
          .price-text {
            font-size: 14px;
            color: #666;
            
            &.active-price {
              color: #FF375D;
              font-weight: 500;
            }
          }
        }
      }
    }
    
    // 座位图区域
    .seat-area {
      background: #E8ECF0;
      border-radius: 8px;
      padding: 30px 20px;
      min-height: 500px;
      
      .venue-wrapper {
        max-width: 1000px;
        margin: 0 auto;
        
        // 舞台提示框
        .stage-box {
          width: 200px;
          margin: 0 auto 20px;
          padding: 12px 0;
          background: #fff;
          border: 1px solid #ddd;
          text-align: center;
          
          span {
            font-size: 20px;
            color: #333;
            font-weight: 500;
          }
        }
        
        // 场馆轮廓
        .venue-outline {
          background: #fff;
          border-radius: 8px;
          padding: 30px 20px 40px;
          position: relative;
          border: 3px solid #d0d5dc;
          
          // 上方梯形效果
          &::before {
            content: '';
            position: absolute;
            top: -3px;
            left: 50%;
            transform: translateX(-50%);
            width: 70%;
            height: 3px;
            background: #fff;
          }
          
          .seat-map-container {
            .seat-row {
              display: flex;
              align-items: center;
              justify-content: center;
              margin-bottom: 6px;
              
              .row-label {
                width: 50px;
                font-size: 12px;
                color: #999;
                text-align: center;
                flex-shrink: 0;
              }
              
              .seats {
                display: flex;
                justify-content: center;
                gap: 4px;
                flex: 1;
                max-width: 700px;
                
                .seat {
                  width: 16px;
                  height: 16px;
                  display: flex;
                  align-items: center;
                  justify-content: center;
                  cursor: pointer;
                  transition: transform 0.15s;
                  
                  &:hover:not(.seat-sold) {
                    transform: scale(1.3);
                  }
                  
                  &.seat-sold {
                    cursor: not-allowed;
                  }
                  
                  &.seat-selected {
                    .seat-dot {
                      transform: scale(1.2);
                    }
                  }
                  
                  .seat-dot {
                    width: 12px;
                    height: 12px;
                    border-radius: 50%;
                    transition: all 0.15s;
                  }
                }
              }
            }
          }
        }
      }
    }
    
    // 底部栏
    .bottom-bar {
      position: fixed;
      bottom: 0;
      left: 0;
      right: 0;
      background: #fff;
      box-shadow: 0 -2px 10px rgba(0, 0, 0, 0.08);
      padding: 12px 20px;
      display: flex;
      justify-content: space-between;
      align-items: center;
      z-index: 100;
      
      .left-section {
        display: flex;
        align-items: center;
        gap: 20px;
        
        .price-display {
          .currency {
            font-size: 16px;
            color: #333;
          }
          
          .amount {
            font-size: 28px;
            font-weight: bold;
            color: #333;
          }
        }
        
        .selected-seats {
          display: flex;
          flex-wrap: wrap;
          gap: 8px;
          max-width: 600px;
          
          .seat-tag {
            display: inline-flex;
            align-items: center;
            padding: 4px 10px;
            background: #FFF0F3;
            color: #FF375D;
            border-radius: 4px;
            font-size: 12px;
            cursor: pointer;
            transition: all 0.2s;
            
            &:hover {
              background: #FFE0E6;
            }
            
            .remove-icon {
              margin-left: 4px;
              font-style: normal;
              font-size: 14px;
            }
          }
        }
      }
      
      .buy-btn {
        padding: 14px 50px;
        font-size: 18px;
        color: #fff;
        background: linear-gradient(135deg, #FF6B9D 0%, #FF375D 100%);
        border: none;
        border-radius: 25px;
        cursor: pointer;
        transition: all 0.3s;
        
        &:hover:not(.disabled) {
          transform: translateY(-2px);
          box-shadow: 0 4px 15px rgba(255, 55, 93, 0.4);
        }
        
        &.disabled {
          background: #ccc;
          cursor: not-allowed;
        }
      }
    }
  }
}
</style>
