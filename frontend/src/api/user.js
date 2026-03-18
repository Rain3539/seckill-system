import request from '../utils/request.js'

export const registerApi = (data) => request.post('/user/register', data)
export const loginApi = (data) => request.post('/user/login', data)
