import request from '../utils/request.js'
export const getProductListApi   = ()   => request.get('/product/list')
export const getProductDetailApi = (id) => request.get(`/product/${id}`)
