import request from '../utils/request.js'

export const placeOrderApi    = (data)    => request.post('/order/place', data)
export const payOrderApi      = (orderNo) => request.post(`/order/pay/${orderNo}`)
export const cancelOrderApi   = (orderNo) => request.post(`/order/cancel/${orderNo}`)
export const getMyOrdersApi   = ()        => request.get('/order/my')
export const getOrderDetailApi = (orderNo) => request.get(`/order/${orderNo}`)
