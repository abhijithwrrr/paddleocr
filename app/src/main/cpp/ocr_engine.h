#pragma once

#include <map>
#include <memory>
#include <string>
#include <vector>
#include <opencv2/core.hpp>

class DetPredictor;
class RecPredictor;
class ClsPredictor;

struct OcrLine {
  std::string text;
  float score;
  std::vector<std::vector<int>> points;
};

class OcrEngine {
 public:
  OcrEngine(const std::string &detModelPath,
            const std::string &recModelPath,
            const std::string &clsModelPath,
            const std::string &configPath,
            const std::string &dictPath,
            int cpuThreadNum,
            const std::string &cpuPowerMode);

  ~OcrEngine();

  std::vector<OcrLine> Recognize(const cv::Mat &bgrImage, int maxSideLen);

 private:
  std::unique_ptr<DetPredictor> det_predictor_;
  std::unique_ptr<RecPredictor> rec_predictor_;
  std::unique_ptr<ClsPredictor> cls_predictor_;
  std::map<std::string, double> config_;
  std::vector<std::string> dict_;
  bool use_direction_classify_ = true;
};
