import request from '../utils/request.js'
export const getSeckillListApi   = ()     => request.get('/seckill/list')
export const getSeckillDetailApi = (id)   => request.get(`/seckill/${id}`)
export const doSeckillApi        = (data) => request.post('/seckill/do', data)
export const warmUpApi           = (id)   => request.post(`/seckill/warmup/${id}`)
