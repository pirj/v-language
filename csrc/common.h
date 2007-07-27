#ifndef COMMON_H
#define COMMON_H
#include <map>
struct cmp_str {
    bool operator()(char const *a, char const *b) {
        return std::strcmp(a, b) < 0;
    }
};

struct Quote;
typedef std::map<char*, Quote*, cmp_str> QMap;

static const double Precision = 0.0000000000000001;
static const int MaxBuf = 1024;

#endif