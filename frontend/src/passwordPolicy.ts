export const minPasswordLength = 6
export const maxPasswordLength = 18
export const passwordPattern = '(?=.*[A-Za-z])(?=.*[0-9])[A-Za-z0-9_@]{' + minPasswordLength + ',' + maxPasswordLength + '}'
export const passwordRule = minPasswordLength + '–' + maxPasswordLength + ' 个字符，至少包含一个英文字母和一个数字。只允许英文字母、数字、_ 和 @，区分大小写。'
