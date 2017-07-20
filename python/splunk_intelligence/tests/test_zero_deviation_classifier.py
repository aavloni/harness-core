import numpy as np
from core.ZeroDeviationClassifier import ZeroDeviationClassifier

def test_fit():
    zdc = ZeroDeviationClassifier()
    zdc.fit_transform(1, np.array([[1, 7],
                                   [1, 7],
                                   [1,7]]), 0.2)
    predictions, score = zdc.predict(1, np.array([[1, 8]]))
    assert score == 1
    assert len(np.where(predictions == -1)[0]) == 0



def test_fit():
    zdc = ZeroDeviationClassifier()
    zdc.fit_transform(1, np.array([[1, 7],
                                   [1, 7],
                                   [1,7]]), 0.3)
    predictions, score = zdc.predict(1, np.array([[1, 8]]))
    assert score == 1
    assert len(np.where(predictions == -1)[0]) == 0