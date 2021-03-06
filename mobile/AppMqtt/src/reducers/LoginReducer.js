import types from '../ultils/constants/actionType';
// import { initialState } from '../store';

const initialState = {
    success : false,
    message:'',
    payload: {
        
    }
}
export default (state = initialState, action) => {
    switch (action.type) {
        case types.LOGIN: {
            return {
               data: action.payload
            };
        }
        case types.LOGIN_ERROR: {
            return {
                message: action.message
            };
        }
        default:
            return state;
    }
};
