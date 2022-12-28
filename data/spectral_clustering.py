import sys

import numpy as np
from sklearn.cluster import SpectralClustering

if __name__ == '__main__':
    k = int(sys.argv[1])
    W = np.loadtxt(r"/home/lvpiao/code/hvmm-source-code/data/wCache.txt")
    try:
        labels = SpectralClustering(
            n_clusters=k,
            assign_labels='kmeans',
            affinity='precomputed',
            eigen_solver='arpack',
            random_state=0).fit_predict(W)
        # sys.stdout = screen
        str_labels = [str(x) for x in labels]
        print(','.join(str_labels))
    except Exception as e:
        print(e)
