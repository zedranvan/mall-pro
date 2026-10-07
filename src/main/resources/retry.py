import functools
import time

def retry(times:int = 3,delay: float =1.0):
    """自定义装饰器：用例失败自动拟人重试"""
    def decorator(func)
        @functools.wraps(func)
        def wrapper(*args,**kwargs):
            for attempt in range(1,times + 1):
                try:
                    return func(*args,**kwargs)
                except AssertionError as e:
                    print(f"[断言失败] 第{attempt}次重试中:{e}")
                    if attempt == times:
                        raise e
                    time.sleep(delay)
        return wrapper
    return decorator
