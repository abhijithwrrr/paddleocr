#include "ocr_engine.h"

#include "cls_process.h"
#include "det_process.h"
#include "rec_process.h"
#include "utils.h"

#include <algorithm>
#include <climits>
#include <cmath>
#include <fstream>
#include <opencv2/imgproc.hpp>
#include <sstream>

namespace {

std::vector<std::string> ReadDict(const std::string &path) {
  std::ifstream in(path);
  std::vector<std::string> lines;
  std::string line;
  if (!in) {
    LOGE("Cannot open dictionary: %s", path.c_str());
    return lines;
  }
  while (std::getline(in, line)) {
    if (!line.empty() && line[line.size() - 1] == '\r') line.pop_back();
    if (lines.empty() && line.size() >= 3 &&
        static_cast<unsigned char>(line[0]) == 0xEF &&
        static_cast<unsigned char>(line[1]) == 0xBB &&
        static_cast<unsigned char>(line[2]) == 0xBF) {
      line = line.substr(3);
    }
    lines.push_back(line);
  }
  return lines;
}

std::map<std::string, double> LoadConfigTxt(const std::string &path) {
  std::map<std::string, double> config;
  std::ifstream in(path);
  std::string line;
  if (!in) {
    LOGE("Cannot open config: %s", path.c_str());
    return config;
  }
  while (std::getline(in, line)) {
    if (line.empty() || line[0] == '#') continue;
    std::istringstream stream(line);
    std::string key;
    double value;
    if (stream >> key >> value) config[key] = value;
  }
  return config;
}

cv::Mat GetRotateCropImage(const cv::Mat &srcimage,
                           const std::vector<std::vector<int>> &box) {
  if (box.size() < 4) return {};

  std::vector<std::vector<int>> points = box;
  int x_collect[4] = {box[0][0], box[1][0], box[2][0], box[3][0]};
  int y_collect[4] = {box[0][1], box[1][1], box[2][1], box[3][1]};
  int left = std::max(0, int(*std::min_element(x_collect, x_collect + 4)));
  int right = std::min(srcimage.cols, int(*std::max_element(x_collect, x_collect + 4)));
  int top = std::max(0, int(*std::min_element(y_collect, y_collect + 4)));
  int bottom = std::min(srcimage.rows, int(*std::max_element(y_collect, y_collect + 4)));
  if (right - left <= 1 || bottom - top <= 1) return {};

  cv::Mat img_crop;
  srcimage(cv::Rect(left, top, right - left, bottom - top)).copyTo(img_crop);

  for (auto &point : points) {
    point[0] -= left;
    point[1] -= top;
  }

  int img_crop_width = static_cast<int>(std::sqrt(
      std::pow(points[0][0] - points[1][0], 2) +
      std::pow(points[0][1] - points[1][1], 2)));
  int img_crop_height = static_cast<int>(std::sqrt(
      std::pow(points[0][0] - points[3][0], 2) +
      std::pow(points[0][1] - points[3][1], 2)));
  if (img_crop_width <= 1 || img_crop_height <= 1) return {};

  cv::Point2f pts_std[4];
  pts_std[0] = cv::Point2f(0.f, 0.f);
  pts_std[1] = cv::Point2f(img_crop_width, 0.f);
  pts_std[2] = cv::Point2f(img_crop_width, img_crop_height);
  pts_std[3] = cv::Point2f(0.f, img_crop_height);

  cv::Point2f pointsf[4];
  for (int i = 0; i < 4; ++i) {
    pointsf[i] = cv::Point2f(points[i][0], points[i][1]);
  }

  cv::Mat transform = cv::getPerspectiveTransform(pointsf, pts_std);
  cv::Mat dst_img;
  cv::warpPerspective(img_crop, dst_img, transform,
                      cv::Size(img_crop_width, img_crop_height),
                      cv::BORDER_REPLICATE);
  if (dst_img.rows >= dst_img.cols * 1.5f) {
    cv::Mat transposed;
    cv::transpose(dst_img, transposed);
    cv::flip(transposed, dst_img, 0);
  }
  return dst_img;
}

int MinX(const std::vector<std::vector<int>> &points) {
  int value = INT_MAX;
  for (const auto &point : points) value = std::min(value, point[0]);
  return value;
}

int MinY(const std::vector<std::vector<int>> &points) {
  int value = INT_MAX;
  for (const auto &point : points) value = std::min(value, point[1]);
  return value;
}

}  // namespace

OcrEngine::OcrEngine(const std::string &detModelPath,
                     const std::string &recModelPath,
                     const std::string &clsModelPath,
                     const std::string &configPath,
                     const std::string &dictPath,
                     int cpuThreadNum,
                     const std::string &cpuPowerMode) {
  config_ = LoadConfigTxt(configPath);
  dict_ = ReadDict(dictPath);
  dict_.insert(dict_.begin(), "#");
  dict_.push_back(" ");
  use_direction_classify_ = config_.count("use_direction_classify") == 0 ||
      config_["use_direction_classify"] >= 1.0;

  cls_predictor_.reset(new ClsPredictor(clsModelPath, cpuThreadNum, cpuPowerMode));
  det_predictor_.reset(new DetPredictor(detModelPath, cpuThreadNum, cpuPowerMode));
  rec_predictor_.reset(new RecPredictor(recModelPath, cpuThreadNum, cpuPowerMode));

  LOGI("OCR engine ready. det=%s rec=%s cls=%s dict=%zu",
       detModelPath.c_str(), recModelPath.c_str(), clsModelPath.c_str(), dict_.size());
}

OcrEngine::~OcrEngine() = default;

std::vector<OcrLine> OcrEngine::Recognize(const cv::Mat &bgrImage, int maxSideLen) {
  std::vector<OcrLine> lines;
  if (bgrImage.empty()) return lines;

  cv::Mat image = bgrImage.clone();
  auto config = config_;
  if (maxSideLen > 0) config["max_side_len"] = maxSideLen;

  auto detect_start = GetCurrentTime();
  auto boxes = det_predictor_->Predict(image, config, nullptr, nullptr, nullptr);
  LOGI("Detected %zu text boxes in %.1f ms", boxes.size(),
       GetElapsedTime(detect_start));

  for (auto &box : boxes) {
    cv::Mat crop = GetRotateCropImage(image, box);
    if (crop.empty()) continue;

    if (use_direction_classify_) {
      crop = cls_predictor_->Predict(crop, nullptr, nullptr, nullptr, 0.9f);
    }

    auto result = rec_predictor_->Predict(crop, nullptr, nullptr, nullptr, dict_);
    if (result.first.empty()) continue;

    OcrLine line;
    line.text = result.first;
    line.score = result.second;
    line.points = box;
    lines.push_back(std::move(line));
  }

  std::stable_sort(lines.begin(), lines.end(), [](const OcrLine &a, const OcrLine &b) {
    const int ay = MinY(a.points);
    const int by = MinY(b.points);
    if (std::abs(ay - by) > 12) return ay < by;
    return MinX(a.points) < MinX(b.points);
  });

  LOGI("Recognized %zu text lines", lines.size());
  return lines;
}
