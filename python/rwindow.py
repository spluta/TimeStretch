import numpy as np

def phis(N):
    alphas = np.arange(N)/(N-1)
    return np.pi*alphas/2

def f_scl(N):
    alphas = np.arange(N)/(N-1)
    return alphas/(1 - alphas)

def f_tan2(N):
    return np.tan(phis(N))**2

def f_tan(N):
    return np.tan(phis(N))

def w_i(r, fs):
    return np.sqrt(1/(1 + 2*fs*r + fs**2))

def w(r, fs):
    return fs * w_i(r, fs)

def xfade_with_windows(x1, x2, r, fs):
    w1 = w(r, fs)
    w2 = w_i(r, fs)
    mix = w1*x1 + w2*x2
    return mix

variable_window_funcs = {
    'w_tan': f_tan,
    'w_tan2': f_tan2,
    'w_scl': f_scl
}
