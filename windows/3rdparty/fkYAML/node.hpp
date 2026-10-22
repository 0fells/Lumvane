//  _______   __ __   __  _____   __  __  __
// |   __| |_/  |  \_/  |/  _  \ /  \/  \|  |     fkYAML: A C++ header-only YAML library
// |   __|  _  < \_   _/|  ___  |    _   |  |___  version 0.4.2
// |__|  |_| \__|  |_|  |_|   |_|___||___|______| https://github.com/fktn-k/fkYAML
//
// SPDX-FileCopyrightText: 2023-2025 Kensuke Fukutani <fktn.dev@gmail.com>
// SPDX-License-Identifier: MIT

#pragma once

#include <algorithm>
#include <cstdint>
#include <cstring>
#include <initializer_list>
#include <map>
#include <memory>
#include <string>
#include <type_traits>
#include <vector>

#ifndef FK_YAML_DETAIL_MACROS_DEFINE_MACROS_HPP
#define FK_YAML_DETAIL_MACROS_DEFINE_MACROS_HPP

#if defined(FK_YAML_MAJOR_VERSION) && defined(FK_YAML_MINOR_VERSION) && defined(FK_YAML_PATCH_VERSION)
#if FK_YAML_MAJOR_VERSION != 0 || FK_YAML_MINOR_VERSION != 4 || FK_YAML_PATCH_VERSION != 2
#warning Already included a different version of the fkYAML library!
#else
#define FK_YAML_VERCHECK_SUCCEEDED
#endif
#endif

#ifndef FK_YAML_VERCHECK_SUCCEEDED

#define FK_YAML_MAJOR_VERSION 0
#define FK_YAML_MINOR_VERSION 4
#define FK_YAML_PATCH_VERSION 2

#define FK_YAML_NAMESPACE_VERSION_CONCAT_IMPL(major, minor, patch) v##major##_##minor##_##patch

#define FK_YAML_NAMESPACE_VERSION_CONCAT(major, minor, patch) FK_YAML_NAMESPACE_VERSION_CONCAT_IMPL(major, minor, patch)

#define FK_YAML_NAMESPACE_VERSION                                                                                      \
    FK_YAML_NAMESPACE_VERSION_CONCAT(FK_YAML_MAJOR_VERSION, FK_YAML_MINOR_VERSION, FK_YAML_PATCH_VERSION)

#define FK_YAML_NAMESPACE_BEGIN                                                                                        \
    namespace fkyaml {                                                                                                 \
    inline namespace FK_YAML_NAMESPACE_VERSION {

#define FK_YAML_NAMESPACE_END                                                                                          \
    } \
    }

#define FK_YAML_DETAIL_NAMESPACE_BEGIN                                                                                 \
    FK_YAML_NAMESPACE_BEGIN                                                                                            \
    namespace detail {

#define FK_YAML_DETAIL_NAMESPACE_END                                                                                   \
    } \
    FK_YAML_NAMESPACE_END

#endif

#ifndef FK_YAML_DETAIL_MACROS_CPP_CONFIG_MACROS_HPP
#define FK_YAML_DETAIL_MACROS_CPP_CONFIG_MACROS_HPP

#if defined(_MSVC_LANG) && !defined(__clang__)
#define FK_YAML_CPLUSPLUS _MSVC_LANG
#else
#define FK_YAML_CPLUSPLUS __cplusplus
#endif

#if !defined(FK_YAML_HAS_CXX_23) && !defined(FK_YAML_HAS_CXX_20) && !defined(FK_YAML_HAS_CXX_17) &&                    \
    !defined(FK_YAML_HAS_CXX_14) && !defined(FK_YAML_CXX_11)
#if FK_YAML_CPLUSPLUS >= 202302L
#define FK_YAML_HAS_CXX_23
#define FK_YAML_HAS_CXX_20
#define FK_YAML_HAS_CXX_17
#define FK_YAML_HAS_CXX_14
#elif FK_YAML_CPLUSPLUS >= 202002L
#define FK_YAML_HAS_CXX_20
#define FK_YAML_HAS_CXX_17
#define FK_YAML_HAS_CXX_14
#elif FK_YAML_CPLUSPLUS >= 201703L
#define FK_YAML_HAS_CXX_17
#define FK_YAML_HAS_CXX_14
#elif FK_YAML_CPLUSPLUS >= 201402L
#define FK_YAML_HAS_CXX_14
#endif

#define FK_YAML_HAS_CXX_11
#endif

#if defined(FK_YAML_HAS_CXX_14)
#define FK_YAML_DEPRECATED(msg) [[deprecated(msg)]]
#else
#if defined(_MSC_VER)
#define FK_YAML_DEPRECATED(msg) __declspec(deprecated(msg))
#elif defined(__GNUC__) || defined(__clang__)
#define FK_YAML_DEPRECATED(msg) __attribute__((deprecated(msg)))
#else
#define FK_YAML_DEPRECATED(msg)
#endif
#endif

#if defined(FK_YAML_HAS_CXX_17)
#define FK_YAML_INLINE_VAR inline
#else
#define FK_YAML_INLINE_VAR
#endif

#if defined(FK_YAML_HAS_CXX_17)
#define FK_YAML_CXX17_CONSTEXPR constexpr
#else
#define FK_YAML_CXX17_CONSTEXPR
#endif

#ifdef __has_include
#define FK_YAML_HAS_INCLUDE(header) __has_include(header)
#else
#define FK_YAML_HAS_INCLUDE(header) (0)
#endif

#ifdef __has_builtin
#define FK_YAML_HAS_BUILTIN(builtin) __has_builtin(builtin)
#else
#define FK_YAML_HAS_BUILTIN(builtin) (0)
#endif

#ifdef __has_cpp_attribute
#define FK_YAML_HAS_CPP_ATTRIBUTE(attr) __has_cpp_attribute(attr)
#else
#define FK_YAML_HAS_CPP_ATTRIBUTE(attr) (0)
#endif

#ifdef __has_feature
#define FK_YAML_HAS_FEATURE(feat) __has_feature(feat)
#else
#define FK_YAML_HAS_FEATURE(feat) (0)
#endif

#if defined(__clang__) && FK_YAML_HAS_FEATURE(address_sanitizer)
#define FK_YAML_NO_SANITIZE(...) __attribute__((no_sanitize(__VA_ARGS__)))
#else
#define FK_YAML_NO_SANITIZE(...)
#endif

#if FK_YAML_HAS_INCLUDE(<version>)
#include <version>
#endif

#if defined(FK_YAML_HAS_CXX_17) && defined(__cpp_lib_to_chars) && __cpp_lib_to_chars >= 201611L
#define FK_YAML_HAS_TO_CHARS (1)
#else
#define FK_YAML_HAS_TO_CHARS (0)
#endif

#if defined(FK_YAML_HAS_CXX_20) && defined(__cpp_char8_t) && __cpp_char8_t >= 201811L
#define FK_YAML_HAS_CHAR8_T (1)
#else
#define FK_YAML_HAS_CHAR8_T (0)
#endif

#if defined(FK_YAML_HAS_CXX_20) && FK_YAML_HAS_CPP_ATTRIBUTE(likely) >= 201803L
#define FK_YAML_LIKELY(expr) (!!(expr)) [[likely]]
#elif FK_YAML_HAS_BUILTIN(__builtin_expect)
#define FK_YAML_LIKELY(expr) (__builtin_expect(!!(expr), 1))
#else
#define FK_YAML_LIKELY(expr) (!!(expr))
#endif

#if defined(FK_YAML_HAS_CXX_20) && FK_YAML_HAS_CPP_ATTRIBUTE(unlikely) >= 201803L
#define FK_YAML_UNLIKELY(expr) (!!(expr)) [[unlikely]]
#elif FK_YAML_HAS_BUILTIN(__builtin_expect)
#define FK_YAML_UNLIKELY(expr) (__builtin_expect(!!(expr), 0))
#else
#define FK_YAML_UNLIKELY(expr) (!!(expr))
#endif

#endif


#endif

#ifndef FK_YAML_DETAIL_ASSERT_HPP
#define FK_YAML_DETAIL_ASSERT_HPP

#ifndef FK_YAML_ASSERT
#ifndef NDEBUG
#include <cassert>
#define FK_YAML_ASSERT(x) assert(x)
#else
#define FK_YAML_ASSERT(x)
#endif
#endif

#endif

#ifndef FK_YAML_DETAIL_DOCUMENT_METAINFO_HPP
#define FK_YAML_DETAIL_DOCUMENT_METAINFO_HPP

#include <string>
#include <map>

#ifndef FK_YAML_DETAIL_META_NODE_TRAITS_HPP
#define FK_YAML_DETAIL_META_NODE_TRAITS_HPP

#ifndef FK_YAML_DETAIL_META_DETECT_HPP
#define FK_YAML_DETAIL_META_DETECT_HPP

#include <iterator>
#include <type_traits>

#ifndef FK_YAML_DETAIL_META_STL_SUPPLEMENT_HPP
#define FK_YAML_DETAIL_META_STL_SUPPLEMENT_HPP

#include <cstddef>
#include <type_traits>

#ifdef FK_YAML_HAS_CXX_14
#include <utility>
#endif

FK_YAML_DETAIL_NAMESPACE_BEGIN

#ifndef FK_YAML_HAS_CXX_14

template <typename T>
using add_pointer_t = typename std::add_pointer<T>::type;

template <bool Condition, typename T = void>
using enable_if_t = typename std::enable_if<Condition, T>::type;

template <typename T>
struct is_null_pointer : std::is_same<std::nullptr_t, typename std::remove_cv<T>::type> {};

template <typename T>
using remove_cv_t = typename std::remove_cv<T>::type;

template <typename T>
using remove_pointer_t = typename std::remove_pointer<T>::type;

template <typename T>
using remove_reference_t = typename std::remove_reference<T>::type;

template <typename T, T... I>
struct integer_sequence {
    using value_type = T;
    static constexpr std::size_t size() noexcept {
        return sizeof...(I);
    }
};

#if !FK_YAML_HAS_BUILTIN(__make_integer_seq) && !FK_YAML_HAS_BUILTIN(__integer_pack)

namespace make_int_seq_impl {

template <typename IntSeq0, typename IntSeq1>
struct merger;

template <typename T, T... Ints0, T... Ints1>
struct merger<integer_sequence<T, Ints0...>, integer_sequence<T, Ints1...>> {
    using type = integer_sequence<T, Ints0..., (sizeof...(Ints0) + Ints1)...>;
};

template <typename T, std::size_t Num>
struct generator {
    using type =
        typename merger<typename generator<T, Num / 2>::type, typename generator<T, Num - Num / 2>::type>::type;
};

template <typename T>
struct generator<T, 0> {
    using type = integer_sequence<T>;
};

template <typename T>
struct generator<T, 1> {
    using type = integer_sequence<T, 0>;
};

}

#endif

template <typename T, T Num>
using make_integer_sequence
#if FK_YAML_HAS_BUILTIN(__make_integer_seq)
    = __make_integer_seq<integer_sequence, T, Num>;
#elif FK_YAML_HAS_BUILTIN(__integer_pack)
    = integer_sequence<T, __integer_pack(Num)...>;
#else
    = typename make_int_seq_impl::generator<T, Num>::type;
#endif

template <std::size_t... Idx>
using index_sequence = integer_sequence<std::size_t, Idx...>;

template <std::size_t Num>
using make_index_sequence = make_integer_sequence<std::size_t, Num>;

template <typename... Types>
using index_sequence_for = make_index_sequence<sizeof...(Types)>;

#else

using std::add_pointer_t;
using std::enable_if_t;
using std::index_sequence;
using std::index_sequence_for;
using std::integer_sequence;
using std::is_null_pointer;
using std::make_index_sequence;
using std::make_integer_sequence;
using std::remove_cv_t;
using std::remove_pointer_t;
using std::remove_reference_t;

#endif

#ifndef FK_YAML_HAS_CXX_17

template <bool Val>
using bool_constant = std::integral_constant<bool, Val>;

template <typename... Traits>
struct conjunction : std::true_type {};

template <typename Trait>
struct conjunction<Trait> : Trait {};

template <typename First, typename... Rest>
struct conjunction<First, Rest...> : std::conditional<First::value, conjunction<Rest...>, First>::type {};

template <typename... Traits>
struct disjunction : std::false_type {};

template <typename Trait>
struct disjunction<Trait> : Trait {};

template <typename First, typename... Rest>
struct disjunction<First, Rest...> : std::conditional<First::value, First, disjunction<Rest...>>::type {};

template <typename Trait>
struct negation : std::integral_constant<bool, !Trait::value> {};

template <typename... Types>
struct make_void {
    using type = void;
};

template <typename... Types>
using void_t = typename make_void<Types...>::type;

#else

using std::bool_constant;
using std::conjunction;
using std::disjunction;
using std::negation;
using std::void_t;

#endif

#ifndef FK_YAML_HAS_CXX_20

template <typename T>
using remove_cvref_t = typename std::remove_cv<typename std::remove_reference<T>::type>::type;

#else

using std::remove_cvref_t;

#endif

[[noreturn]] inline void unreachable() {
#if defined(FK_YAML_HAS_CXX_23) || (defined(__cpp_lib_unreachable) && __cpp_lib_unreachable >= 202202L)
    std::unreachable();
#elif defined(_MSC_VER) && !defined(__clang__)
    __assume(false);
#else
    __builtin_unreachable();
#endif
}

FK_YAML_DETAIL_NAMESPACE_END

#endif


FK_YAML_DETAIL_NAMESPACE_BEGIN

struct nonesuch {
    nonesuch() = delete;
    ~nonesuch() = delete;
    nonesuch(const nonesuch&) = delete;
    nonesuch(nonesuch&&) = delete;
    nonesuch& operator=(const nonesuch&) = delete;
    nonesuch& operator=(nonesuch&&) = delete;
};

namespace detector_impl {

template <typename Default, typename AlwaysVoid, template <typename...> class Op, typename... Args>
struct detector : std::false_type {
    using type = Default;
};

template <typename Default, template <typename...> class Op, typename... Args>
struct detector<Default, void_t<Op<Args...>>, Op, Args...> : std::true_type {
    using type = Op<Args...>;
};

}

template <template <typename...> class Op, typename... Args>
using is_detected = detector_impl::detector<nonesuch, void, Op, Args...>;

template <template <typename...> class Op, typename... Args>
using detected_t = typename detector_impl::detector<nonesuch, void, Op, Args...>::type;

template <typename Expected, template <typename...> class Op, typename... Args>
using is_detected_exact = std::is_same<Expected, detected_t<Op, Args...>>;

namespace detect {

template <typename T>
using iterator_t = typename T::iterator;

template <typename T>
using key_type_t = typename T::key_type;

template <typename T>
using mapped_type_t = typename T::mapped_type;

template <typename T>
using value_type_t = typename T::value_type;

template <typename T>
using difference_type_t = typename T::difference_type;

template <typename T>
using pointer_t = typename T::pointer;

template <typename T>
using reference_t = typename T::reference;

template <typename T>
using iterator_category_t = typename T::iterator_category;

template <typename T>
using container_type_t = typename T::container_type;

template <typename T, typename... Args>
using emplace_fn_t = decltype(std::declval<T>().emplace(std::declval<Args>()...));

template <typename T>
using reserve_fn_t = decltype(std::declval<T>().reserve(std::declval<typename remove_cvref_t<T>::size_type>()));

template <typename T>
using has_iterator = is_detected<iterator_t, remove_cvref_t<T>>;

template <typename T>
using has_key_type = is_detected<key_type_t, remove_cvref_t<T>>;

template <typename T>
using has_mapped_type = is_detected<mapped_type_t, remove_cvref_t<T>>;

template <typename T>
using has_value_type = is_detected<value_type_t, remove_cvref_t<T>>;

template <typename T>
struct is_iterator_traits : conjunction<
                                is_detected<difference_type_t, remove_cvref_t<T>>, has_value_type<remove_cvref_t<T>>,
                                is_detected<pointer_t, remove_cvref_t<T>>, is_detected<reference_t, remove_cvref_t<T>>,
                                is_detected<iterator_category_t, remove_cvref_t<T>>> {};

template <typename T>
using has_container_type = is_detected<container_type_t, remove_cvref_t<T>>;

template <typename T>
using has_reserve = is_detected<reserve_fn_t, T>;

using std::begin;
using std::end;

template <typename T, typename = void>
struct has_begin_end : std::false_type {};

template <typename T>
struct has_begin_end<T, void_t<decltype(begin(std::declval<T>()), end(std::declval<T>()))>> : std::true_type {};

}

FK_YAML_DETAIL_NAMESPACE_END

#endif

#ifndef FK_YAML_DETAIL_META_TYPE_TRAITS_HPP
#define FK_YAML_DETAIL_META_TYPE_TRAITS_HPP

#include <iterator>
#include <type_traits>

FK_YAML_DETAIL_NAMESPACE_BEGIN

template <typename Comparator, typename T, typename U, typename = void>
struct is_comparable : std::false_type {};

template <typename Comparator, typename T, typename U>
struct is_comparable<
    Comparator, T, U,
    void_t<
        decltype(std::declval<Comparator>()(std::declval<T>(), std::declval<U>())),
        decltype(std::declval<Comparator>()(std::declval<U>(), std::declval<T>()))>> : std::true_type {};

template <typename Comparator, typename ObjectKeyType, typename KeyType>
using is_usable_as_key_type = is_comparable<Comparator, ObjectKeyType, KeyType>;

template <typename T>
using is_non_bool_integral = conjunction<std::is_integral<T>, negation<std::is_same<bool, T>>>;

template <typename T, typename = void>
struct is_complete_type : std::false_type {};

template <typename T>
struct is_complete_type<T, decltype(void(sizeof(T)))> : std::true_type {};

template <typename ItrType, typename T>
using is_iterator_of = std::is_same<remove_cv_t<typename std::iterator_traits<ItrType>::value_type>, T>;

template <typename T>
struct static_const {
    static FK_YAML_INLINE_VAR constexpr T value {};
};

#ifndef FK_YAML_HAS_CXX_17
template <typename T>
constexpr T static_const<T>::value;
#endif

template <typename T>
struct type_tag {
    using type = T;
};

FK_YAML_DETAIL_NAMESPACE_END

#endif

#ifndef FK_YAML_FKYAML_FWD_HPP
#define FK_YAML_FKYAML_FWD_HPP

#include <cstdint>
#include <map>
#include <string>
#include <vector>

FK_YAML_NAMESPACE_BEGIN

template <typename ValueType, typename = void>
class node_value_converter;

template <
    template <typename, typename...> class SequenceType = std::vector,
    template <typename, typename, typename...> class MappingType = std::map, typename BooleanType = bool,
    typename IntegerType = std::int64_t, typename FloatNumberType = double, typename StringType = std::string,
    template <typename, typename = void> class ConverterType = node_value_converter>
class basic_node;

using node = basic_node<>;

template <typename Key, typename Value, typename IgnoredCompare, typename Allocator>
class ordered_map;

FK_YAML_NAMESPACE_END

#endif


FK_YAML_DETAIL_NAMESPACE_BEGIN

template <typename T>
struct is_basic_node_impl : std::false_type {};

template <
    template <typename, typename...> class SequenceType, template <typename, typename, typename...> class MappingType,
    typename BooleanType, typename IntegerType, typename FloatNumberType, typename StringType,
    template <typename, typename> class Converter>
struct is_basic_node_impl<
    basic_node<SequenceType, MappingType, BooleanType, IntegerType, FloatNumberType, StringType, Converter>>
    : std::true_type {};

template <typename T>
struct is_basic_node : is_basic_node_impl<remove_cvref_t<T>> {};

template <typename>
class node_ref_storage;

template <typename T>
struct is_node_ref_storage : std::false_type {};

template <typename T>
struct is_node_ref_storage<node_ref_storage<T>> : std::true_type {};

template <typename T, typename... Args>
using from_node_function_t = decltype(T::from_node(std::declval<Args>()...));

template <typename T, typename... Args>
using to_node_function_t = decltype(T::to_node(std::declval<Args>()...));

template <typename BasicNodeType, typename T, typename = void>
struct has_from_node : std::false_type {};

template <typename BasicNodeType, typename T>
struct has_from_node<BasicNodeType, T, enable_if_t<negation<is_basic_node<T>>::value>> {
    using converter = typename BasicNodeType::template value_converter_type<T, void>;

    static constexpr bool value =
        is_detected_exact<void, from_node_function_t, converter, const BasicNodeType&, T&>::value;
};

template <typename BasicNodeType, typename T, typename = void>
struct has_to_node : std::false_type {};

template <typename BasicNodeType, typename T>
struct has_to_node<BasicNodeType, T, enable_if_t<negation<is_basic_node<T>>::value>> {
    using converter = typename BasicNodeType::template value_converter_type<T, void>;

    static constexpr bool value = is_detected_exact<void, to_node_function_t, converter, BasicNodeType&, T>::value;
};

template <typename BasicNodeType, typename CompatibleType, typename = void>
struct is_node_compatible_type_impl : std::false_type {};

template <typename BasicNodeType, typename CompatibleType>
struct is_node_compatible_type_impl<
    BasicNodeType, CompatibleType,
    enable_if_t<conjunction<is_complete_type<CompatibleType>, has_to_node<BasicNodeType, CompatibleType>>::value>>
    : std::true_type {};

template <typename BasicNodeType, typename CompatibleType>
struct is_node_compatible_type : is_node_compatible_type_impl<BasicNodeType, CompatibleType> {};

FK_YAML_DETAIL_NAMESPACE_END

#endif

#ifndef FK_YAML_YAML_VERSION_TYPE_HPP
#define FK_YAML_YAML_VERSION_TYPE_HPP

#include <cstdint>

FK_YAML_NAMESPACE_BEGIN

enum class yaml_version_type : std::uint8_t {
    VERSION_1_1,
    VERSION_1_2,
};

inline const char* to_string(yaml_version_type t) noexcept {
    switch (t) {
    case yaml_version_type::VERSION_1_1:
        return "VERSION_1_1";
    case yaml_version_type::VERSION_1_2:
        return "VERSION_1_2";
    default:
        detail::unreachable();
    }
}

FK_YAML_NAMESPACE_END

#endif


FK_YAML_DETAIL_NAMESPACE_BEGIN

template <typename BasicNodeType, typename = enable_if_t<is_basic_node<BasicNodeType>::value>>
struct document_metainfo {
    yaml_version_type version {yaml_version_type::VERSION_1_2};
    bool is_version_specified {false};
    std::string primary_handle_prefix;
    std::string secondary_handle_prefix;
    std::map<std::string, std::string > named_handle_map;
    std::multimap<std::string, BasicNodeType> anchor_table {};
};

FK_YAML_DETAIL_NAMESPACE_END

#endif

#ifndef FK_YAML_DETAIL_EXCEPTION_SAFE_ALLOCATION_HPP
#define FK_YAML_DETAIL_EXCEPTION_SAFE_ALLOCATION_HPP

#include <memory>
#include <utility>

FK_YAML_DETAIL_NAMESPACE_BEGIN

template <typename ObjT, typename AllocTraits>
struct tidy_guard {
    tidy_guard() = delete;

    tidy_guard(ObjT* p_obj) noexcept
        : p_obj(p_obj) {
    }

    tidy_guard(const tidy_guard&) = delete;
    tidy_guard& operator=(const tidy_guard&) = delete;

    tidy_guard(tidy_guard&&) = default;

    tidy_guard& operator=(tidy_guard&&) = default;

    ~tidy_guard() {
        if FK_YAML_UNLIKELY (p_obj != nullptr) {
            typename AllocTraits::allocator_type alloc {};
            AllocTraits::destroy(alloc, p_obj);
            AllocTraits::deallocate(alloc, p_obj, 1);
            p_obj = nullptr;
        }
    }

    ObjT* get() const noexcept {
        return p_obj;
    }

    explicit operator bool() const noexcept {
        return p_obj != nullptr;
    }

    ObjT* release() noexcept {
        ObjT* ret = p_obj;
        p_obj = nullptr;
        return ret;
    }

    ObjT* p_obj {nullptr};
};

template <typename ObjT, typename... Args>
inline ObjT* create_object(Args&&... args) {
    using alloc_type = std::allocator<ObjT>;
    using alloc_traits_type = std::allocator_traits<alloc_type>;

    alloc_type alloc {};
    tidy_guard<ObjT, alloc_traits_type> tg {alloc_traits_type::allocate(alloc, 1)};
    alloc_traits_type::construct(alloc, tg.get(), std::forward<Args>(args)...);

    FK_YAML_ASSERT(tg);
    return tg.release();
}

template <typename ObjT>
inline void destroy_object(ObjT* p_obj) {
    FK_YAML_ASSERT(p_obj != nullptr);
    std::allocator<ObjT> alloc;
    std::allocator_traits<decltype(alloc)>::destroy(alloc, p_obj);
    std::allocator_traits<decltype(alloc)>::deallocate(alloc, p_obj, 1);
}

FK_YAML_DETAIL_NAMESPACE_END

#endif

#ifndef FK_YAML_DETAIL_INPUT_DESERIALIZER_HPP
#define FK_YAML_DETAIL_INPUT_DESERIALIZER_HPP

#include <algorithm>
#include <deque>
#include <vector>

#ifndef FK_YAML_DETAIL_INPUT_LEXICAL_ANALYZER_HPP
#define FK_YAML_DETAIL_INPUT_LEXICAL_ANALYZER_HPP

#include <algorithm>
#include <cctype>
#include <cstdlib>

#ifndef FK_YAML_DETAIL_ENCODINGS_URI_ENCODING_HPP
#define FK_YAML_DETAIL_ENCODINGS_URI_ENCODING_HPP

#include <cctype>
#include <string>

FK_YAML_DETAIL_NAMESPACE_BEGIN

class uri_encoding {
public:
    static bool validate(const char* begin, const char* end) noexcept {
        if (begin == end) {
            return true;
        }

        const char* current = begin;

        for (; current != end; ++current) {
            if (*current == '%') {
                const bool are_valid_octets = validate_octets(++current, end);
                if (!are_valid_octets) {
                    return false;
                }

                continue;
            }

            const bool is_allowed_character = validate_character(*current);
            if (!is_allowed_character) {
                return false;
            }
        }

        return true;
    }

private:
    static bool validate_octets(const char*& begin, const char*& end) {
        for (int i = 0; i < 2; i++, ++begin) {
            if (begin == end) {
                return false;
            }

            const int octet = std::tolower(*begin);

            if ('0' <= octet && octet <= '9') {
                continue;
            }

            if ('a' <= octet && octet <= 'f') {
                continue;
            }

            return false;
        }

        return true;
    }

    static bool validate_character(const char c) {
        switch (c) {
        case ':':
        case '/':
        case '?':
        case '#':
        case '[':
        case ']':
        case '@':
        case '!':
        case '$':
        case '&':
        case '\'':
        case '(':
        case ')':
        case '*':
        case '+':
        case ',':
        case ';':
        case '=':
        case '-':
        case '.':
        case '_':
        case '~':
            return true;
        default:
            return static_cast<bool>(std::isalnum(c));
        }
    }
};

FK_YAML_DETAIL_NAMESPACE_END

#endif

#ifndef FK_YAML_DETAIL_ENCODINGS_UTF_ENCODINGS_HPP
#define FK_YAML_DETAIL_ENCODINGS_UTF_ENCODINGS_HPP

#include <array>
#include <cstdint>

#ifndef FK_YAML_EXCEPTION_HPP
#define FK_YAML_EXCEPTION_HPP

#include <array>
#include <initializer_list>
#include <stdexcept>
#include <string>

#ifndef FK_YAML_DETAIL_STRING_FORMATTER_HPP
#define FK_YAML_DETAIL_STRING_FORMATTER_HPP

#include <cstdarg>
#include <cstdio>
#include <memory>
#include <string>

FK_YAML_DETAIL_NAMESPACE_BEGIN

inline std::string format(const char* fmt, ...) {
    va_list vl;
    va_start(vl, fmt);
    int size = std::vsnprintf(nullptr, 0, fmt, vl);
    va_end(vl);

    if (size < 0) {
        return "";
    }

    const std::unique_ptr<char[]> buffer {new char[size + 1] {}};

    va_start(vl, fmt);
    size = std::vsnprintf(buffer.get(), size + 1, fmt, vl);
    va_end(vl);

    return {buffer.get(), static_cast<std::size_t>(size)};
}

FK_YAML_DETAIL_NAMESPACE_END

#endif

#ifndef FK_YAML_DETAIL_TYPES_NODE_T_HPP
#define FK_YAML_DETAIL_TYPES_NODE_T_HPP

#include <cstdint>

#ifndef FK_YAML_NODE_TYPE_HPP
#define FK_YAML_NODE_TYPE_HPP

#include <cstdint>

FK_YAML_NAMESPACE_BEGIN

enum class node_type : std::uint8_t {
    SEQUENCE,
    MAPPING,
    NULL_OBJECT,
    BOOLEAN,
    INTEGER,
    FLOAT,
    STRING,
};

inline const char* to_string(node_type t) noexcept {
    switch (t) {
    case node_type::SEQUENCE:
        return "SEQUENCE";
    case node_type::MAPPING:
        return "MAPPING";
    case node_type::NULL_OBJECT:
        return "NULL_OBJECT";
    case node_type::BOOLEAN:
        return "BOOLEAN";
    case node_type::INTEGER:
        return "INTEGER";
    case node_type::FLOAT:
        return "FLOAT";
    case node_type::STRING:
        return "STRING";
    default:
        detail::unreachable();
    }
}

FK_YAML_NAMESPACE_END

#endif


FK_YAML_DETAIL_NAMESPACE_BEGIN

enum class node_t : std::uint8_t {
    SEQUENCE,
    MAPPING,
    NULL_OBJECT,
    BOOLEAN,
    INTEGER,
    FLOAT_NUMBER,
    STRING,
};

inline const char* to_string(node_t t) noexcept {
    switch (t) {
    case node_t::SEQUENCE:
        return "sequence";
    case node_t::MAPPING:
        return "mapping";
    case node_t::NULL_OBJECT:
        return "null";
    case node_t::BOOLEAN:
        return "boolean";
    case node_t::INTEGER:
        return "integer";
    case node_t::FLOAT_NUMBER:
        return "float";
    case node_t::STRING:
        return "string";
    default:
        detail::unreachable();
    }
}

inline node_t convert_from_node_type(node_type t) {
    switch (t) {
    case node_type::SEQUENCE:
        return node_t::SEQUENCE;
    case node_type::MAPPING:
        return node_t::MAPPING;
    case node_type::NULL_OBJECT:
        return node_t::NULL_OBJECT;
    case node_type::BOOLEAN:
        return node_t::BOOLEAN;
    case node_type::INTEGER:
        return node_t::INTEGER;
    case node_type::FLOAT:
        return node_t::FLOAT_NUMBER;
    case node_type::STRING:
        return node_t::STRING;
    default:
        detail::unreachable();
    }
}

inline node_type convert_to_node_type(node_t t) {
    switch (t) {
    case node_t::SEQUENCE:
        return node_type::SEQUENCE;
    case node_t::MAPPING:
        return node_type::MAPPING;
    case node_t::NULL_OBJECT:
        return node_type::NULL_OBJECT;
    case node_t::BOOLEAN:
        return node_type::BOOLEAN;
    case node_t::INTEGER:
        return node_type::INTEGER;
    case node_t::FLOAT_NUMBER:
        return node_type::FLOAT;
    case node_t::STRING:
        return node_type::STRING;
    default:
        detail::unreachable();
    }
}

FK_YAML_DETAIL_NAMESPACE_END

#endif


FK_YAML_NAMESPACE_BEGIN

class exception : public std::exception {
public:
    exception() = default;

    explicit exception(const char* msg) noexcept {
        if (msg) {
            m_error_msg = msg;
        }
    }

public:
    const char* what() const noexcept override {
        return m_error_msg.c_str();
    }

private:
    std::string m_error_msg;
};

class invalid_encoding : public exception {
public:
    explicit invalid_encoding(const char* msg, const std::initializer_list<uint8_t>& u8) noexcept
        : exception(generate_error_message(msg, u8).c_str()) {
    }

    explicit invalid_encoding(const char* msg, std::array<char16_t, 2> u16) noexcept
        : exception(generate_error_message(msg, u16).c_str()) {
    }

    explicit invalid_encoding(const char* msg, char32_t u32) noexcept
        : exception(generate_error_message(msg, u32).c_str()) {
    }

private:
    static std::string generate_error_message(const char* msg, const std::initializer_list<uint8_t>& u8) noexcept {
        const auto* itr = u8.begin();
        const auto* end_itr = u8.end();
        std::string formatted = detail::format("invalid_encoding: %s in=[ 0x%02x", msg, *itr++);
        while (itr != end_itr) {
            formatted += detail::format(", 0x%02x", *itr++);
        }
        formatted += " ]";
        return formatted;
    }

    static std::string generate_error_message(const char* msg, std::array<char16_t, 2> u16) noexcept {
        return detail::format(
            "invalid_encoding: %s in=[ 0x%04x, 0x%04x ]",
            msg,
            static_cast<uint16_t>(u16[0]),
            static_cast<uint16_t>(u16[1]));
    }

    static std::string generate_error_message(const char* msg, char32_t u32) noexcept {
        return detail::format("invalid_encoding: %s in=0x%08x", msg, static_cast<uint32_t>(u32));
    }
};

class parse_error : public exception {
public:
    explicit parse_error(const char* msg, uint32_t lines, uint32_t cols_in_line) noexcept
        : exception(generate_error_message(msg, lines, cols_in_line).c_str()) {
    }

private:
    static std::string generate_error_message(const char* msg, uint32_t lines, uint32_t cols_in_line) noexcept {
        return detail::format("parse_error: %s (at line %u, column %u)", msg, lines, cols_in_line);
    }
};

class type_error : public exception {
public:
    explicit type_error(const char* msg, node_type type) noexcept
        : exception(generate_error_message(msg, type).c_str()) {
    }

    FK_YAML_DEPRECATED("Since 0.3.12; Use explicit type_error(const char*, node_type)")
    explicit type_error(const char* msg, detail::node_t type) noexcept
        : type_error(msg, detail::convert_to_node_type(type)) {
    }

private:
    static std::string generate_error_message(const char* msg, node_type type) noexcept {
        return detail::format("type_error: %s type=%s", msg, to_string(type));
    }
};

class out_of_range : public exception {
public:
    explicit out_of_range(int index) noexcept
        : exception(generate_error_message(index).c_str()) {
    }

    explicit out_of_range(const char* key) noexcept
        : exception(generate_error_message(key).c_str()) {
    }

private:
    static std::string generate_error_message(int index) noexcept {
        return detail::format("out_of_range: index %d is out of range", index);
    }

    static std::string generate_error_message(const char* key) noexcept {
        return detail::format("out_of_range: key \'%s\' is not found.", key);
    }
};

class invalid_tag : public exception {
public:
    explicit invalid_tag(const char* msg, const char* tag)
        : exception(generate_error_message(msg, tag).c_str()) {
    }

private:
    static std::string generate_error_message(const char* msg, const char* tag) noexcept {
        return detail::format("invalid_tag: %s tag=%s", msg, tag);
    }
};

FK_YAML_NAMESPACE_END

#endif


FK_YAML_DETAIL_NAMESPACE_BEGIN

namespace utf8 {

inline uint32_t get_num_bytes(uint8_t first_byte) {
    if FK_YAML_LIKELY (first_byte < 0x80) {
        return 1;
    }
    if ((first_byte & 0xE0) == 0xC0) {
        return 2;
    }
    if ((first_byte & 0xF0) == 0xE0) {
        return 3;
    }
    if ((first_byte & 0xF8) == 0xF0) {
        return 4;
    }

    throw fkyaml::invalid_encoding("Invalid UTF-8 encoding.", {first_byte});
}

inline bool validate(uint8_t byte) noexcept {
    return byte <= 0x7Fu;
}

inline bool validate(uint8_t byte0, uint8_t byte1) noexcept {
    if FK_YAML_LIKELY (0xC2u <= byte0 && byte0 <= 0xDFu) {
        if FK_YAML_LIKELY (0x80u <= byte1 && byte1 <= 0xBFu) {
            return true;
        }
    }

    return false;
}

inline bool validate(uint8_t byte0, uint8_t byte1, uint8_t byte2) noexcept {
    if (0xE0u <= byte0 && byte0 <= 0xECu) {
        if FK_YAML_LIKELY (0x80u <= byte1 && byte1 <= 0xBFu) {
            if FK_YAML_LIKELY (0x80u <= byte2 && byte2 <= 0xBFu) {
                return true;
            }
        }
        return false;
    }

    if (byte0 == 0xEDu) {
        if FK_YAML_LIKELY (0x80u <= byte1 && byte1 <= 0x9Fu) {
            if FK_YAML_LIKELY (0x80u <= byte2 && byte2 <= 0xBFu) {
                return true;
            }
        }
        return false;
    }

    if FK_YAML_LIKELY (byte0 == 0xEEu || byte0 == 0xEFu) {
        if FK_YAML_LIKELY (0x80u <= byte1 && byte1 <= 0xBFu) {
            if FK_YAML_LIKELY (0x80u <= byte2 && byte2 <= 0xBFu) {
                return true;
            }
        }
    }

    return false;
}

inline bool validate(uint8_t byte0, uint8_t byte1, uint8_t byte2, uint8_t byte3) noexcept {
    if (byte0 == 0xF0u) {
        if FK_YAML_LIKELY (0x90u <= byte1 && byte1 <= 0xBFu) {
            if FK_YAML_LIKELY (0x80u <= byte2 && byte2 <= 0xBFu) {
                if FK_YAML_LIKELY (0x80u <= byte3 && byte3 <= 0xBFu) {
                    return true;
                }
            }
        }
        return false;
    }

    if (0xF1u <= byte0 && byte0 <= 0xF3u) {
        if FK_YAML_LIKELY (0x80u <= byte1 && byte1 <= 0xBFu) {
            if FK_YAML_LIKELY (0x80u <= byte2 && byte2 <= 0xBFu) {
                if FK_YAML_LIKELY (0x80u <= byte3 && byte3 <= 0xBFu) {
                    return true;
                }
            }
        }
        return false;
    }

    if FK_YAML_LIKELY (byte0 == 0xF4u) {
        if FK_YAML_LIKELY (0x80u <= byte1 && byte1 <= 0x8Fu) {
            if FK_YAML_LIKELY (0x80u <= byte2 && byte2 <= 0xBFu) {
                if FK_YAML_LIKELY (0x80u <= byte3 && byte3 <= 0xBFu) {
                    return true;
                }
            }
        }
    }

    return false;
}

inline void from_utf16(
    std::array<char16_t, 2> utf16, std::array<uint8_t, 4>& utf8, uint32_t& consumed_size, uint32_t& encoded_size) {
    const auto first = utf16[0];
    const auto second = utf16[1];
    if (first < 0x80u) {
        utf8[0] = static_cast<uint8_t>(first & 0x7Fu);
        consumed_size = 1;
        encoded_size = 1;
    }
    else if (first <= 0x7FFu) {
        const auto utf8_chunk = static_cast<uint16_t>(0xC080u | ((first & 0x07C0u) << 2) | (first & 0x3Fu));
        utf8[0] = static_cast<uint8_t>(utf8_chunk >> 8);
        utf8[1] = static_cast<uint8_t>(utf8_chunk);
        consumed_size = 1;
        encoded_size = 2;
    }
    else if (first < 0xD800u || 0xE000u <= first) {
        const uint32_t utf8_chunk = 0xE08080u | ((first & 0xF000u) << 4) | ((first & 0x0FC0u) << 2) | (first & 0x3Fu);
        utf8[0] = static_cast<uint8_t>(utf8_chunk >> 16);
        utf8[1] = static_cast<uint8_t>(utf8_chunk >> 8);
        utf8[2] = static_cast<uint8_t>(utf8_chunk);
        consumed_size = 1;
        encoded_size = 3;
    }
    else if (first <= 0xDBFFu && 0xDC00u <= second && second <= 0xDFFFu) {
        const uint32_t code_point = 0x10000u + ((first & 0x03FFu) << 10) + (second & 0x03FFu);
        const uint32_t utf8_chunk = 0xF0808080u | ((code_point & 0x1C0000u) << 6) | ((code_point & 0x03F000u) << 4) |
                                    ((code_point & 0x0FC0u) << 2) | (code_point & 0x3Fu);
        utf8[0] = static_cast<uint8_t>(utf8_chunk >> 24);
        utf8[1] = static_cast<uint8_t>(utf8_chunk >> 16);
        utf8[2] = static_cast<uint8_t>(utf8_chunk >> 8);
        utf8[3] = static_cast<uint8_t>(utf8_chunk);
        consumed_size = 2;
        encoded_size = 4;
    }
    else {
        throw invalid_encoding("Invalid UTF-16 encoding detected.", utf16);
    }
}

inline void from_utf32(const char32_t utf32, std::array<uint8_t, 4>& utf8, uint32_t& encoded_size) {
    if (utf32 < 0x80u) {
        utf8[0] = static_cast<uint8_t>(utf32 & 0x007F);
        encoded_size = 1;
    }
    else if (utf32 <= 0x7FFu) {
        const auto utf8_chunk = static_cast<uint16_t>(0xC080u | ((utf32 & 0x07C0u) << 2) | (utf32 & 0x3Fu));
        utf8[0] = static_cast<uint8_t>(utf8_chunk >> 8);
        utf8[1] = static_cast<uint8_t>(utf8_chunk);
        encoded_size = 2;
    }
    else if (utf32 <= 0xFFFFu) {
        const uint32_t utf8_chunk = 0xE08080u | ((utf32 & 0xF000u) << 4) | ((utf32 & 0x0FC0u) << 2) | (utf32 & 0x3F);
        utf8[0] = static_cast<uint8_t>(utf8_chunk >> 16);
        utf8[1] = static_cast<uint8_t>(utf8_chunk >> 8);
        utf8[2] = static_cast<uint8_t>(utf8_chunk);
        encoded_size = 3;
    }
    else if (utf32 <= 0x10FFFFu) {
        const uint32_t utf8_chunk = 0xF0808080u | ((utf32 & 0x1C0000u) << 6) | ((utf32 & 0x03F000u) << 4) |
                                    ((utf32 & 0x0FC0u) << 2) | (utf32 & 0x3Fu);
        utf8[0] = static_cast<uint8_t>(utf8_chunk >> 24);
        utf8[1] = static_cast<uint8_t>(utf8_chunk >> 16);
        utf8[2] = static_cast<uint8_t>(utf8_chunk >> 8);
        utf8[3] = static_cast<uint8_t>(utf8_chunk);
        encoded_size = 4;
    }
    else {
        throw invalid_encoding("Invalid UTF-32 encoding detected.", utf32);
    }
}

}

FK_YAML_DETAIL_NAMESPACE_END

#endif

#ifndef FK_YAML_DETAIL_INPUT_BLOCK_SCALAR_HEADER_HPP
#define FK_YAML_DETAIL_INPUT_BLOCK_SCALAR_HEADER_HPP

#include <cstdint>

FK_YAML_DETAIL_NAMESPACE_BEGIN

enum class chomping_indicator_t : std::uint8_t {
    STRIP,
    CLIP,
    KEEP,
};

struct block_scalar_header {
    chomping_indicator_t chomp {chomping_indicator_t::CLIP};
    uint32_t indent {0};
};

FK_YAML_DETAIL_NAMESPACE_END

#endif

#ifndef FK_YAML_DETAIL_INPUT_POSITION_TRACKER_HPP
#define FK_YAML_DETAIL_INPUT_POSITION_TRACKER_HPP

#include <algorithm>

#ifndef FK_YAML_DETAIL_STR_VIEW_HPP
#define FK_YAML_DETAIL_STR_VIEW_HPP

#include <limits>
#include <string>

FK_YAML_DETAIL_NAMESPACE_BEGIN

template <typename CharT, typename Traits = std::char_traits<CharT>>
class basic_str_view {
    static_assert(!std::is_array<CharT>::value, "CharT must not be an array type.");
    static_assert(
        std::is_trivial<CharT>::value && std::is_standard_layout<CharT>::value,
        "CharT must be a trivial, standard layout type.");
    static_assert(
        std::is_same<CharT, typename Traits::char_type>::value, "CharT & Traits::char_type must be the same type.");

public:
    using traits_type = Traits;
    using value_type = CharT;
    using pointer = value_type*;
    using const_pointer = const value_type*;
    using reference = value_type&;
    using const_reference = const value_type&;
    using const_iterator = const value_type*;
    using iterator = const_iterator;
    using const_reverse_iterator = std::reverse_iterator<const_iterator>;
    using reverse_iterator = const_reverse_iterator;
    using size_type = std::size_t;
    using difference_type = std::ptrdiff_t;

    static constexpr size_type npos = static_cast<size_type>(-1);

    constexpr basic_str_view() noexcept = default;

    ~basic_str_view() noexcept = default;

    constexpr basic_str_view(const basic_str_view&) noexcept = default;

    constexpr basic_str_view(basic_str_view&&) noexcept = default;

    template <
        typename CharPtrT,
        enable_if_t<
            conjunction<
                negation<std::is_array<CharPtrT>>, std::is_pointer<CharPtrT>,
                disjunction<std::is_same<CharPtrT, value_type*>, std::is_same<CharPtrT, const value_type*>>>::value,
            int> = 0>
    FK_YAML_CXX17_CONSTEXPR basic_str_view(CharPtrT p_str) noexcept
        : m_len(traits_type::length(p_str)),
          mp_str(p_str) {
    }

    template <std::size_t N>
    constexpr basic_str_view(const value_type (&str)[N]) noexcept
        : m_len(N - 1),
          mp_str(&str[0]) {
    }

    basic_str_view(std::nullptr_t) = delete;

    constexpr basic_str_view(const value_type* p_str, size_type len) noexcept
        : m_len(len),
          mp_str(p_str) {
    }

    template <
        typename ItrType,
        enable_if_t<
            conjunction<
                is_iterator_of<ItrType, CharT>,
                std::is_base_of<
                    std::random_access_iterator_tag, typename std::iterator_traits<ItrType>::iterator_category>>::value,
            int> = 0>
    basic_str_view(ItrType first, ItrType last) noexcept
        : m_len(last - first),
          mp_str(&*first) {
    }

    basic_str_view(const std::basic_string<CharT>& str) noexcept
        : m_len(str.length()),
          mp_str(str.data()) {
    }

    basic_str_view& operator=(const basic_str_view&) noexcept = default;

    basic_str_view& operator=(basic_str_view&&) noexcept = default;

    const_iterator begin() const noexcept {
        return mp_str;
    }

    const_iterator end() const noexcept {
        return mp_str + m_len;
    }

    const_iterator cbegin() const noexcept {
        return mp_str;
    }

    const_iterator cend() const noexcept {
        return mp_str + m_len;
    }

    const_reverse_iterator rbegin() const noexcept {
        return const_reverse_iterator(end());
    }

    const_reverse_iterator rend() const noexcept {
        return const_reverse_iterator(begin());
    }

    const_reverse_iterator crbegin() const noexcept {
        return const_reverse_iterator(end());
    }

    const_reverse_iterator crend() const noexcept {
        return const_reverse_iterator(begin());
    }

    size_type size() const noexcept {
        return m_len;
    }

    size_type length() const noexcept {
        return m_len;
    }

    constexpr size_type max_size() const noexcept {
        return static_cast<size_type>(std::numeric_limits<difference_type>::max());
    }

    bool empty() const noexcept {
        return m_len == 0;
    }

    const_reference operator[](size_type pos) const noexcept {
        return *(mp_str + pos);
    }

    const_reference at(size_type pos) const {
        if FK_YAML_UNLIKELY (pos >= m_len) {
            throw fkyaml::out_of_range(static_cast<int>(pos));
        }
        return *(mp_str + pos);
    }

    const_reference front() const noexcept {
        return *mp_str;
    }

    const_reference back() const {
        return *(mp_str + m_len - 1);
    }

    const_pointer data() const noexcept {
        return mp_str;
    }

    void remove_prefix(size_type n) noexcept {
        mp_str += n;
        m_len -= n;
    }

    void remove_suffix(size_type n) noexcept {
        m_len -= n;
    }

    void swap(basic_str_view& other) noexcept {
        auto tmp = *this;
        *this = other;
        other = tmp;
    }

    size_type copy(CharT* p_str, size_type n, size_type pos = 0) const {
        if FK_YAML_UNLIKELY (pos > m_len) {
            throw fkyaml::out_of_range(static_cast<int>(pos));
        }
        const size_type rlen = std::min(n, m_len - pos);
        traits_type::copy(p_str, mp_str + pos, rlen);
        return rlen;
    }

    basic_str_view substr(size_type pos = 0, size_type n = npos) const {
        if FK_YAML_UNLIKELY (pos > m_len) {
            throw fkyaml::out_of_range(static_cast<int>(pos));
        }
        const size_type rlen = std::min(n, m_len - pos);
        return basic_str_view(mp_str + pos, rlen);
    }

    int compare(basic_str_view sv) const noexcept {
        const size_type rlen = std::min(m_len, sv.m_len);
        int ret = traits_type::compare(mp_str, sv.mp_str, rlen);

        if (ret == 0) {
            using int_limits = std::numeric_limits<int>;
            const difference_type diff =
                m_len > sv.m_len ? m_len - sv.m_len
                                 : static_cast<difference_type>(-1) * static_cast<difference_type>(sv.m_len - m_len);

            if (diff > int_limits::max()) {
                ret = int_limits::max();
            }
            else if (diff < int_limits::min()) {
                ret = int_limits::min();
            }
            else {
                ret = static_cast<int>(diff);
            }
        }

        return ret;
    }

    int compare(size_type pos1, size_type n1, basic_str_view sv) const {
        return substr(pos1, n1).compare(sv);
    }

    int compare(size_type pos1, size_type n1, basic_str_view sv, size_type pos2, size_type n2) const {
        return substr(pos1, n1).compare(sv.substr(pos2, n2));
    }

    int compare(const CharT* s) const {
        return compare(basic_str_view(s));
    }

    int compare(size_type pos1, size_type n1, const CharT* s) const {
        return substr(pos1, n1).compare(basic_str_view(s));
    }

    int compare(size_type pos1, size_type n1, const CharT* s, size_type n2) const {
        return substr(pos1, n1).compare(basic_str_view(s, n2));
    }

    bool starts_with(basic_str_view sv) const {
        return substr(0, sv.size()) == sv;
    }

    bool starts_with(CharT c) const noexcept {
        return !empty() && traits_type::eq(front(), c);
    }

    bool starts_with(const CharT* s) const {
        return starts_with(basic_str_view(s));
    }

    bool ends_with(basic_str_view sv) const noexcept {
        const size_type size = m_len;
        const size_type sv_size = sv.size();
        return size >= sv_size && traits_type::compare(end() - sv_size, sv.data(), sv_size) == 0;
    }

    bool ends_with(CharT c) const noexcept {
        return !empty() && traits_type::eq(back(), c);
    }

    bool ends_with(const CharT* s) const noexcept {
        return ends_with(basic_str_view(s));
    }

    bool contains(basic_str_view sv) const noexcept {
        return find(sv) != npos;
    }

    bool contains(CharT c) const noexcept {
        return find(c) != npos;
    }

    bool contains(const CharT* s) const noexcept {
        return find(s) != npos;
    }

    size_type find(basic_str_view sv, size_type pos = 0) const noexcept {
        return find(sv.mp_str, pos, sv.m_len);
    }

    size_type find(CharT c, size_type pos = 0) const noexcept {
        size_type ret = npos;

        if FK_YAML_LIKELY (pos < m_len) {
            const size_type n = m_len - pos;
            const CharT* p_found = traits_type::find(mp_str + pos, n, c);
            if (p_found) {
                ret = p_found - mp_str;
            }
        }

        return ret;
    }

    size_type find(const CharT* s, size_type pos, size_type n) const noexcept {
        if FK_YAML_UNLIKELY (n == 0) {
            return pos <= m_len ? pos : npos;
        }

        if FK_YAML_UNLIKELY (pos >= m_len) {
            return npos;
        }

        CharT s0 = s[0];
        const CharT* p_first = mp_str + pos;
        const CharT* p_last = mp_str + m_len;
        size_type len = m_len - pos;

        while (len >= n) {
            p_first = traits_type::find(p_first, len - n + 1, s0);
            if (!p_first) {
                return npos;
            }

            if (traits_type::compare(p_first, s, n) == 0) {
                return p_first - mp_str;
            }

            len = p_last - (++p_first);
        }

        return npos;
    }

    size_type find(const CharT* s, size_type pos = 0) const noexcept {
        return find(basic_str_view(s), pos);
    }

    size_type rfind(basic_str_view sv, size_type pos = npos) const noexcept {
        return rfind(sv.mp_str, pos, sv.m_len);
    }

    size_type rfind(CharT c, size_type pos = npos) const noexcept {
        if FK_YAML_UNLIKELY (m_len == 0) {
            return npos;
        }

        const size_type idx = std::min(m_len - 1, pos);

        for (size_type i = 0; i <= idx; i++) {
            if (traits_type::eq(mp_str[idx - i], c)) {
                return idx - i;
            }
        }

        return npos;
    }

    size_type rfind(const CharT* s, size_type pos, size_type n) const noexcept {
        if FK_YAML_LIKELY (n <= m_len) {
            pos = std::min(m_len - n, pos) + 1;

            do {
                if (traits_type::compare(mp_str + --pos, s, n) == 0) {
                    return pos;
                }
            } while (pos > 0);
        }

        return npos;
    }

    size_type rfind(const CharT* s, size_type pos = npos) const noexcept {
        return rfind(basic_str_view(s), pos);
    }

    size_type find_first_of(basic_str_view sv, size_type pos = 0) const noexcept {
        return find_first_of(sv.mp_str, pos, sv.m_len);
    }

    size_type find_first_of(CharT c, size_type pos = 0) const noexcept {
        return find(c, pos);
    }

    size_type find_first_of(const CharT* s, size_type pos, size_type n) const noexcept {
        if FK_YAML_UNLIKELY (n == 0) {
            return npos;
        }

        for (size_type idx = pos; idx < m_len; ++idx) {
            const CharT* p_found = traits_type::find(s, n, mp_str[idx]);
            if (p_found) {
                return idx;
            }
        }

        return npos;
    }

    size_type find_first_of(const CharT* s, size_type pos = 0) const noexcept {
        return find_first_of(basic_str_view(s), pos);
    }

    size_type find_last_of(basic_str_view sv, size_type pos = npos) const noexcept {
        return find_last_of(sv.mp_str, pos, sv.m_len);
    }

    size_type find_last_of(CharT c, size_type pos = npos) const noexcept {
        return rfind(c, pos);
    }

    size_type find_last_of(const CharT* s, size_type pos, size_type n) const noexcept {
        if FK_YAML_LIKELY (n <= m_len) {
            pos = std::min(m_len - n - 1, pos);

            do {
                const CharT* p_found = traits_type::find(s, n, mp_str[pos]);
                if (p_found) {
                    return pos;
                }
            } while (pos-- != 0);
        }

        return npos;
    }

    size_type find_last_of(const CharT* s, size_type pos = npos) const noexcept {
        return find_last_of(basic_str_view(s), pos);
    }

    size_type find_first_not_of(basic_str_view sv, size_type pos = 0) const noexcept {
        return find_first_not_of(sv.mp_str, pos, sv.m_len);
    }

    size_type find_first_not_of(CharT c, size_type pos = 0) const noexcept {
        for (; pos < m_len; ++pos) {
            if (!traits_type::eq(mp_str[pos], c)) {
                return pos;
            }
        }

        return npos;
    }

    size_type find_first_not_of(const CharT* s, size_type pos, size_type n) const noexcept {
        for (; pos < m_len; ++pos) {
            const CharT* p_found = traits_type::find(s, n, mp_str[pos]);
            if (!p_found) {
                return pos;
            }
        }

        return npos;
    }

    size_type find_first_not_of(const CharT* s, size_type pos = 0) const noexcept {
        return find_first_not_of(basic_str_view(s), pos);
    }

    size_type find_last_not_of(basic_str_view sv, size_type pos = npos) const noexcept {
        return find_last_not_of(sv.mp_str, pos, sv.m_len);
    }

    size_type find_last_not_of(CharT c, size_type pos = npos) const noexcept {
        if FK_YAML_LIKELY (m_len > 0) {
            pos = std::min(m_len, pos);

            do {
                if (!traits_type::eq(mp_str[--pos], c)) {
                    return pos;
                }
            } while (pos > 0);
        }

        return npos;
    }

    size_type find_last_not_of(const CharT* s, size_type pos, size_type n) const noexcept {
        if FK_YAML_UNLIKELY (n <= m_len) {
            pos = std::min(m_len - n, pos) + 1;

            do {
                const CharT* p_found = traits_type::find(s, n, mp_str[--pos]);
                if (!p_found) {
                    return pos;
                }
            } while (pos > 0);
        }

        return npos;
    }

    size_type find_last_not_of(const CharT* s, size_type pos = npos) const noexcept {
        return find_last_not_of(basic_str_view(s), pos);
    }

private:
    size_type m_len {0};
    const value_type* mp_str {nullptr};
};

#ifndef FK_YAML_HAS_CXX_17

template <typename CharT, typename Traits>
constexpr typename basic_str_view<CharT, Traits>::size_type basic_str_view<CharT, Traits>::npos;

#endif

template <typename CharT, typename Traits>
inline bool operator==(basic_str_view<CharT, Traits> lhs, basic_str_view<CharT, Traits> rhs) noexcept {
    return lhs.size() == rhs.size() && lhs.compare(rhs) == 0;
}

template <typename CharT, typename Traits>
inline bool operator==(basic_str_view<CharT, Traits> lhs, const std::basic_string<CharT, Traits>& rhs) noexcept {
    return lhs == basic_str_view<CharT, Traits>(rhs);
}

template <typename CharT, typename Traits>
inline bool operator==(const std::basic_string<CharT, Traits>& lhs, basic_str_view<CharT, Traits> rhs) noexcept {
    return basic_str_view<CharT, Traits>(lhs) == rhs;
}

template <typename CharT, typename Traits, std::size_t N>
inline bool operator==(basic_str_view<CharT, Traits> lhs, const CharT (&rhs)[N]) noexcept {
    return lhs == basic_str_view<CharT, Traits>(rhs);
}

template <typename CharT, typename Traits, std::size_t N>
inline bool operator==(const CharT (&lhs)[N], basic_str_view<CharT, Traits> rhs) noexcept {
    return basic_str_view<CharT, Traits>(lhs) == rhs;
}

template <typename CharT, typename Traits>
inline bool operator!=(basic_str_view<CharT, Traits> lhs, basic_str_view<CharT, Traits> rhs) noexcept {
    return !(lhs == rhs);
}

template <typename CharT, typename Traits>
inline bool operator!=(basic_str_view<CharT, Traits> lhs, const std::basic_string<CharT, Traits>& rhs) noexcept {
    return !(lhs == basic_str_view<CharT, Traits>(rhs));
}

template <typename CharT, typename Traits>
inline bool operator!=(const std::basic_string<CharT, Traits>& lhs, basic_str_view<CharT, Traits> rhs) noexcept {
    return !(basic_str_view<CharT, Traits>(lhs) == rhs);
}

template <typename CharT, typename Traits, std::size_t N>
inline bool operator!=(basic_str_view<CharT, Traits> lhs, const CharT (&rhs)[N]) noexcept {
    return !(lhs == basic_str_view<CharT, Traits>(rhs, N - 1));
}

template <typename CharT, typename Traits, std::size_t N>
inline bool operator!=(const CharT (&lhs)[N], basic_str_view<CharT, Traits> rhs) noexcept {
    return !(basic_str_view<CharT, Traits>(lhs, N - 1) == rhs);
}

template <typename CharT, typename Traits>
inline bool operator<(basic_str_view<CharT, Traits> lhs, basic_str_view<CharT, Traits> rhs) noexcept {
    return lhs.compare(rhs) < 0;
}

template <typename CharT, typename Traits>
inline bool operator<=(basic_str_view<CharT, Traits> lhs, basic_str_view<CharT, Traits> rhs) noexcept {
    return lhs.compare(rhs) <= 0;
}

template <typename CharT, typename Traits>
inline bool operator>(basic_str_view<CharT, Traits> lhs, basic_str_view<CharT, Traits> rhs) noexcept {
    return lhs.compare(rhs) > 0;
}

template <typename CharT, typename Traits>
inline bool operator>=(basic_str_view<CharT, Traits> lhs, basic_str_view<CharT, Traits> rhs) noexcept {
    return lhs.compare(rhs) >= 0;
}

template <typename CharT, typename Traits>
inline std::basic_ostream<CharT, Traits>& operator<<(
    std::basic_ostream<CharT, Traits>& os, basic_str_view<CharT, Traits> sv) {
    return os.write(sv.data(), static_cast<std::streamsize>(sv.size()));
}

using str_view = basic_str_view<char>;

#if FK_YAML_HAS_CHAR8_T
using u8str_view = basic_str_view<char8_t>;
#endif

using u16str_view = basic_str_view<char16_t>;

using u32str_view = basic_str_view<char32_t>;

FK_YAML_DETAIL_NAMESPACE_END

#endif


FK_YAML_DETAIL_NAMESPACE_BEGIN

class position_tracker {
public:
    void set_target_buffer(str_view buffer) noexcept {
        m_begin = m_last = buffer.begin();
        m_end = buffer.end();
    }

    void update_position(const char* p_current) {
        const auto diff = static_cast<uint32_t>(p_current - m_last);
        if (diff == 0) {
            return;
        }

        m_cur_pos += diff;
        const uint32_t prev_lines_read = m_lines_read;
        m_lines_read += static_cast<uint32_t>(std::count(m_last, p_current, '\n'));
        m_last = p_current;

        if (prev_lines_read == m_lines_read) {
            m_cur_pos_in_line += diff;
            return;
        }

        uint32_t count = 0;
        const char* p_begin = m_begin;
        while (--p_current != p_begin) {
            if (*p_current == '\n') {
                break;
            }
            count++;
        }
        m_cur_pos_in_line = count;
    }

    uint32_t get_cur_pos() const noexcept {
        return m_cur_pos;
    }

    uint32_t get_cur_pos_in_line() const noexcept {
        return m_cur_pos_in_line;
    }

    uint32_t get_lines_read() const noexcept {
        return m_lines_read;
    }

private:
    const char* m_begin {};
    const char* m_end {};
    const char* m_last {};
    uint32_t m_cur_pos {0};
    uint32_t m_cur_pos_in_line {0};
    uint32_t m_lines_read {0};
};

FK_YAML_DETAIL_NAMESPACE_END

#endif

#ifndef FK_YAML_DETAIL_TYPES_LEXICAL_TOKEN_T_HPP
#define FK_YAML_DETAIL_TYPES_LEXICAL_TOKEN_T_HPP

#include <cstdint>

FK_YAML_DETAIL_NAMESPACE_BEGIN

enum class lexical_token_t : std::uint8_t {
    END_OF_BUFFER,
    EXPLICIT_KEY_PREFIX,
    KEY_SEPARATOR,
    VALUE_SEPARATOR,
    ANCHOR_PREFIX,
    ALIAS_PREFIX,
    YAML_VER_DIRECTIVE,
    TAG_DIRECTIVE,
    TAG_PREFIX,
    INVALID_DIRECTIVE,
    SEQUENCE_BLOCK_PREFIX,
    SEQUENCE_FLOW_BEGIN,
    SEQUENCE_FLOW_END,
    MAPPING_FLOW_BEGIN,
    MAPPING_FLOW_END,
    PLAIN_SCALAR,
    SINGLE_QUOTED_SCALAR,
    DOUBLE_QUOTED_SCALAR,
    BLOCK_LITERAL_SCALAR,
    BLOCK_FOLDED_SCALAR,
    END_OF_DIRECTIVES,
    END_OF_DOCUMENT,
};

FK_YAML_DETAIL_NAMESPACE_END

#endif

FK_YAML_DETAIL_NAMESPACE_BEGIN

struct lexical_token {
    lexical_token() = default;

    lexical_token(lexical_token_t t, str_view s) noexcept
        : type(t),
          str(s) {
    }

    lexical_token(lexical_token_t t) noexcept
        : type(t) {
    }

    lexical_token(const lexical_token&) = default;
    lexical_token& operator=(const lexical_token&) = default;
    lexical_token(lexical_token&&) = default;
    lexical_token& operator=(lexical_token&&) = default;
    ~lexical_token() = default;

    lexical_token_t type {lexical_token_t::END_OF_BUFFER};
    str_view str;
};

class lexical_analyzer {
    static constexpr uint32_t flow_context_bit = 1u << 0u;
    static constexpr uint32_t document_directive_bit = 1u << 1u;

public:
    explicit lexical_analyzer(str_view input_buffer) noexcept
        : m_begin_itr(input_buffer.begin()),
          m_cur_itr(input_buffer.begin()),
          m_end_itr(input_buffer.end()) {
        m_pos_tracker.set_target_buffer(input_buffer);
    }

    lexical_token get_next_token() {
        skip_white_spaces_and_newline_codes();

        m_token_begin_itr = m_cur_itr;
        m_pos_tracker.update_position(m_cur_itr);
        m_last_token_begin_pos = m_pos_tracker.get_cur_pos_in_line();
        m_last_token_begin_line = m_pos_tracker.get_lines_read();

        if (m_cur_itr == m_end_itr) {
            return {lexical_token_t::END_OF_BUFFER};
        }

        switch (*m_cur_itr) {
        case '?':
            if (++m_cur_itr == m_end_itr) {
                return {lexical_token_t::PLAIN_SCALAR, {m_token_begin_itr, 1}};
            }

            if (*m_cur_itr == ' ') {
                return {lexical_token_t::EXPLICIT_KEY_PREFIX};
            }
            break;
        case ':':
            if (++m_cur_itr == m_end_itr) {
                return {lexical_token_t::KEY_SEPARATOR};
            }

            switch (*m_cur_itr) {
            case ' ':
            case '\t':
            case '\n':
                return {lexical_token_t::KEY_SEPARATOR};
            default:
                if ((m_state & flow_context_bit) == 0) {
                    break;
                }

                switch (*m_cur_itr) {
                case ',':
                case '[':
                case ']':
                case '{':
                case '}':
                    return {lexical_token_t::KEY_SEPARATOR};
                default:
                    FK_YAML_ASSERT(m_token_begin_itr != m_begin_itr);

                    switch (*(m_token_begin_itr - 1)) {
                    case '\'':
                    case '\"':
                    case ']':
                    case '}':
                        return {lexical_token_t::KEY_SEPARATOR};
                    default:
                        break;
                    }
                    break;
                }
                break;
            }
            break;
        case ',':
            ++m_cur_itr;
            return {lexical_token_t::VALUE_SEPARATOR};
        case '&':
            return {lexical_token_t::ANCHOR_PREFIX, extract_anchor_name()};
        case '*':
            return {lexical_token_t::ALIAS_PREFIX, extract_anchor_name()};
        case '!':
            return {lexical_token_t::TAG_PREFIX, extract_tag_name()};
        case '#':
            scan_comment();
            return get_next_token();
        case '%':
            if (m_state & document_directive_bit) {
                return {scan_directive()};
            }
            break;
        case '-': {
            switch (*(m_cur_itr + 1)) {
            case ' ':
            case '\t':
            case '\n':
                m_cur_itr += 2;
                return {lexical_token_t::SEQUENCE_BLOCK_PREFIX};
            default:
                break;
            }

            if (m_pos_tracker.get_cur_pos_in_line() == 0) {
                if ((m_end_itr - m_cur_itr) > 2) {
                    const bool is_dir_end = std::equal(m_token_begin_itr, m_cur_itr + 3, "---");
                    if (is_dir_end) {
                        m_cur_itr += 3;
                        return {lexical_token_t::END_OF_DIRECTIVES};
                    }
                }
            }

            break;
        }
        case '[':
            ++m_cur_itr;
            return {lexical_token_t::SEQUENCE_FLOW_BEGIN};
        case ']':
            ++m_cur_itr;
            return {lexical_token_t::SEQUENCE_FLOW_END};
        case '{':
            ++m_cur_itr;
            return {lexical_token_t::MAPPING_FLOW_BEGIN};
        case '}':
            ++m_cur_itr;
            return {lexical_token_t::MAPPING_FLOW_END};
        case '@':
            emit_error("Any token cannot start with at(@). It is a reserved indicator for YAML.");
        case '`':
            emit_error("Any token cannot start with grave accent(`). It is a reserved indicator for YAML.");
        case '\"':
            ++m_token_begin_itr;
            return {lexical_token_t::DOUBLE_QUOTED_SCALAR, determine_double_quoted_scalar_range()};
        case '\'':
            ++m_token_begin_itr;
            return {lexical_token_t::SINGLE_QUOTED_SCALAR, determine_single_quoted_scalar_range()};
        case '.': {
            if (m_pos_tracker.get_cur_pos_in_line() == 0) {
                const auto rem_size = m_end_itr - m_cur_itr;
                if FK_YAML_LIKELY (rem_size > 2) {
                    const bool is_doc_end = std::equal(m_cur_itr, m_cur_itr + 3, "...");
                    if (is_doc_end) {
                        if (rem_size > 3) {
                            switch (*(m_cur_itr + 3)) {
                            case ' ':
                            case '\t':
                            case '\n':
                                m_cur_itr += 4;
                                break;
                            default:
                                emit_error("The document end marker \"...\" must not be followed by non-ws char.");
                            }
                        }
                        else {
                            m_cur_itr += 3;
                        }
                        return {lexical_token_t::END_OF_DOCUMENT};
                    }
                }
            }
            break;
        }
        case '|':
        case '>': {
            const str_view sv {m_token_begin_itr, m_end_itr};
            const std::size_t header_end_pos = sv.find('\n');
            FK_YAML_ASSERT(header_end_pos != str_view::npos);
            const uint32_t base_indent = get_current_indent_level(&sv[header_end_pos]);

            const lexical_token_t type = *m_token_begin_itr == '|' ? lexical_token_t::BLOCK_LITERAL_SCALAR
                                                                   : lexical_token_t::BLOCK_FOLDED_SCALAR;
            const str_view header_line = sv.substr(1, header_end_pos - 1);
            m_block_scalar_header = convert_to_block_scalar_header(header_line);

            m_token_begin_itr = sv.begin() + (header_end_pos + 1);

            return {
                type,
                determine_block_scalar_content_range(
                    base_indent, m_block_scalar_header.indent, m_block_scalar_header.indent)};
        }
        default:
            break;
        }

        return {lexical_token_t::PLAIN_SCALAR, determine_plain_scalar_range()};
    }

    uint32_t get_last_token_begin_pos() const noexcept {
        return m_last_token_begin_pos;
    }

    uint32_t get_lines_processed() const noexcept {
        return m_last_token_begin_line;
    }

    str_view get_yaml_version() const noexcept {
        return m_yaml_version;
    }

    str_view get_tag_handle() const noexcept {
        return m_tag_handle;
    }

    str_view get_tag_prefix() const noexcept {
        return m_tag_prefix;
    }

    block_scalar_header get_block_scalar_header() const noexcept {
        return m_block_scalar_header;
    }

    void set_context_state(bool is_flow_context) noexcept {
        m_state &= ~flow_context_bit;
        if (is_flow_context) {
            m_state |= flow_context_bit;
        }
    }

    void set_document_state(bool is_directive) noexcept {
        m_state &= ~document_directive_bit;
        if (is_directive) {
            m_state |= document_directive_bit;
        }
    }

private:
    uint32_t get_current_indent_level(const char* p_line_end) {
        std::size_t line_begin_pos = str_view(m_begin_itr, p_line_end - 1).find_last_of('\n');
        if (line_begin_pos == str_view::npos) {
            line_begin_pos = 0;
        }
        else {
            ++line_begin_pos;
        }
        const char* p_line_begin = m_begin_itr + line_begin_pos;
        const char* cur_itr = p_line_begin;

        uint32_t indent = 0;
        bool indent_found = false;
        uint32_t context = 0;
        while (cur_itr != p_line_end && !indent_found) {
            switch (*cur_itr) {
            case ' ':
                ++indent;
                ++cur_itr;
                break;
            case '-':
                switch (*(cur_itr + 1)) {
                case ' ':
                case '\t':
                    indent += 2;
                    cur_itr += 2;
                    context = 1;
                    break;
                default:
                    indent_found = true;
                    break;
                }
                break;
            case '?':
                if (*(cur_itr + 1) == ' ') {
                    indent += 2;
                    cur_itr += 2;
                    context = 2;
                    break;
                }

                indent_found = true;
                break;
            case ':':
                switch (*(cur_itr + 1)) {
                case ' ':
                case '\t':
                    indent += 2;
                    cur_itr += 2;
                    context = 3;
                    break;
                default:
                    indent_found = true;
                    break;
                }
                break;
            default:
                indent_found = true;
                break;
            }
        }

        if (context > 0) {
            const str_view line_content_part {p_line_begin + indent, p_line_end};
            std::size_t key_sep_pos = line_content_part.find(": ");
            if (key_sep_pos == str_view::npos) {
                key_sep_pos = line_content_part.find(":\t");
            }

            if (key_sep_pos == str_view::npos) {
                constexpr char targets[] = "-?:";
                FK_YAML_ASSERT(context - 1 < sizeof(targets));
                const char target_char = targets[context - 1];

                const str_view line_indent_part {p_line_begin, indent};
                const std::size_t block_seq_item_begin_pos = line_indent_part.find_last_of(target_char);
                FK_YAML_ASSERT(block_seq_item_begin_pos != str_view::npos);
                indent = static_cast<uint32_t>(block_seq_item_begin_pos);
            }
        }

        return indent;
    }

    void scan_comment() {
        FK_YAML_ASSERT(*m_cur_itr == '#');
        if FK_YAML_LIKELY (m_cur_itr != m_begin_itr) {
            switch (*(m_cur_itr - 1)) {
            case ' ':
            case '\t':
            case '\n':
                break;
            default:
                emit_error("Comment must not begin right after non-break characters");
            }
        }
        skip_until_line_end();
    }

    lexical_token_t scan_directive() {
        FK_YAML_ASSERT(*m_cur_itr == '%');

        m_token_begin_itr = ++m_cur_itr;

        bool ends_loop = false;
        while (!ends_loop && m_cur_itr != m_end_itr) {
            switch (*m_cur_itr) {
            case ' ':
            case '\t':
                ends_loop = true;
                break;
            case '\n':
                skip_until_line_end();
                return lexical_token_t::INVALID_DIRECTIVE;
            default:
                ++m_cur_itr;
                break;
            }
        }

        const str_view dir_name(m_token_begin_itr, m_cur_itr);

        if (dir_name == "TAG") {
            if FK_YAML_UNLIKELY (!ends_loop) {
                emit_error("There must be at least one white space between \"%TAG\" and tag info.");
            }
            skip_white_spaces();
            return scan_tag_directive();
        }

        if (dir_name == "YAML") {
            if FK_YAML_UNLIKELY (!ends_loop) {
                emit_error("There must be at least one white space between \"%YAML\" and version.");
            }
            skip_white_spaces();
            return scan_yaml_version_directive();
        }

        skip_until_line_end();
        return lexical_token_t::INVALID_DIRECTIVE;
    }

    lexical_token_t scan_tag_directive() {
        m_token_begin_itr = m_cur_itr;

        if FK_YAML_UNLIKELY (*m_cur_itr != '!') {
            emit_error("Tag handle must start with \'!\'.");
        }

        if FK_YAML_UNLIKELY (++m_cur_itr == m_end_itr) {
            emit_error("invalid TAG directive is found.");
        }

        switch (*m_cur_itr) {
        case ' ':
        case '\t':
            break;
        case '!':
            if FK_YAML_UNLIKELY (++m_cur_itr == m_end_itr) {
                emit_error("invalid TAG directive is found.");
            }
            if FK_YAML_UNLIKELY (*m_cur_itr != ' ' && *m_cur_itr != '\t') {
                emit_error("invalid tag handle is found.");
            }
            break;
        default: {
            bool ends_loop = false;
            do {
                switch (*m_cur_itr) {
                case ' ':
                case '\t':
                    emit_error("invalid tag handle is found.");
                case '!': {
                    if (m_cur_itr + 1 == m_end_itr) {
                        ends_loop = true;
                        break;
                    }
                    const char next = *(m_cur_itr + 1);
                    if FK_YAML_UNLIKELY (next != ' ' && next != '\t') {
                        emit_error("invalid tag handle is found.");
                    }
                    ends_loop = true;
                    break;
                }
                case '-':
                    break;
                default:
                    if FK_YAML_UNLIKELY (!isalnum(*m_cur_itr)) {
                        emit_error("named handle can contain only numbers(0-9), alphabets(A-Z,a-z) and hyphens(-).");
                    }
                    break;
                }

                if FK_YAML_UNLIKELY (++m_cur_itr == m_end_itr) {
                    emit_error("invalid TAG directive is found.");
                }
            } while (!ends_loop);
            break;
        }
        }

        m_tag_handle = str_view {m_token_begin_itr, m_cur_itr};

        skip_white_spaces();

        m_token_begin_itr = m_cur_itr;
        const char* p_tag_prefix_begin = m_cur_itr;
        switch (*m_cur_itr) {
        case ',':
        case '[':
        case ']':
        case '{':
        case '}':
            emit_error("tag prefix must not start with flow indicators (\',\', [], {}).");
        default:
            break;
        }

        bool ends_loop = false;
        do {
            switch (*m_cur_itr) {
            case ' ':
            case '\t':
            case '\n':
                ends_loop = true;
                break;
            default:
                break;
            }
        } while (!ends_loop && ++m_cur_itr != m_end_itr);

        const bool is_valid = uri_encoding::validate(p_tag_prefix_begin, m_cur_itr);
        if FK_YAML_UNLIKELY (!is_valid) {
            emit_error("invalid URI character is found in a tag prefix.");
        }

        m_tag_prefix = str_view {p_tag_prefix_begin, m_cur_itr};

        return lexical_token_t::TAG_DIRECTIVE;
    }

    lexical_token_t scan_yaml_version_directive() {
        m_token_begin_itr = m_cur_itr;

        bool ends_loop = false;
        while (!ends_loop && m_cur_itr != m_end_itr) {
            switch (*m_cur_itr) {
            case ' ':
            case '\t':
            case '\n':
                ends_loop = true;
                break;
            default:
                ++m_cur_itr;
                break;
            }
        }

        m_yaml_version = str_view {m_token_begin_itr, m_cur_itr};

        if FK_YAML_UNLIKELY (m_yaml_version.compare("1.1") != 0 && m_yaml_version.compare("1.2") != 0) {
            emit_error("Only 1.1 and 1.2 can be specified as the YAML version.");
        }

        return lexical_token_t::YAML_VER_DIRECTIVE;
    }

    str_view extract_anchor_name() {
        FK_YAML_ASSERT(*m_cur_itr == '&' || *m_cur_itr == '*');

        m_token_begin_itr = ++m_cur_itr;

        bool ends_loop = false;
        for (; m_cur_itr != m_end_itr; ++m_cur_itr) {
            switch (*m_cur_itr) {
            case ' ':
            case '\t':
            case '\n':
            case '{':
            case '}':
            case '[':
            case ']':
            case ',':
                ends_loop = true;
                break;
            default:
                break;
            }

            if (ends_loop) {
                break;
            }
        }

        if FK_YAML_UNLIKELY (m_token_begin_itr == m_cur_itr) {
            emit_error("anchor name must not be empty.");
        }

        return {m_token_begin_itr, m_cur_itr};
    }

    str_view extract_tag_name() {
        FK_YAML_ASSERT(*m_cur_itr == '!');

        if (++m_cur_itr == m_end_itr) {
            return {m_token_begin_itr, m_end_itr};
        }

        bool is_verbatim = false;
        bool allows_another_tag_prefix = false;

        switch (*m_cur_itr) {
        case ' ':
        case '\n':
            return {m_token_begin_itr, m_cur_itr};
        case '!':
            break;
        case '<':
            is_verbatim = true;
            ++m_cur_itr;
            break;
        default:
            allows_another_tag_prefix = true;
            break;
        }

        bool is_named_handle = false;
        bool ends_loop = false;
        do {
            if (++m_cur_itr == m_end_itr) {
                break;
            }

            switch (*m_cur_itr) {
            case ' ':
            case '\t':
            case '\n':
                ends_loop = true;
                break;
            case '!':
                if FK_YAML_UNLIKELY (!allows_another_tag_prefix) {
                    emit_error("invalid tag prefix (!) is found.");
                }

                is_named_handle = true;
                allows_another_tag_prefix = false;
                break;
            default:
                break;
            }
        } while (!ends_loop);

        str_view tag_name {m_token_begin_itr, m_cur_itr};

        if (is_verbatim) {
            const char last = tag_name.back();
            if FK_YAML_UNLIKELY (last != '>') {
                emit_error("verbatim tag (!<TAG>) must be ended with \'>\'.");
            }

            const str_view tag_body = tag_name.substr(2, tag_name.size() - 3);
            if FK_YAML_UNLIKELY (tag_body.empty()) {
                emit_error("verbatim tag(!<TAG>) must not be empty.");
            }

            const bool is_valid_uri = uri_encoding::validate(tag_body.begin(), tag_body.end());
            if FK_YAML_UNLIKELY (!is_valid_uri) {
                emit_error("invalid URI character is found in a verbatim tag.");
            }

            return tag_name;
        }

        if (is_named_handle) {
            const char last = tag_name.back();
            if FK_YAML_UNLIKELY (last == '!') {
                emit_error("named handle has no suffix.");
            }
        }

        const std::size_t last_tag_prefix_pos = tag_name.find_last_of('!');
        FK_YAML_ASSERT(last_tag_prefix_pos != str_view::npos);

        const str_view tag_uri = tag_name.substr(last_tag_prefix_pos + 1);
        const bool is_valid_uri = uri_encoding::validate(tag_uri.begin(), tag_uri.end());
        if FK_YAML_UNLIKELY (!is_valid_uri) {
            emit_error("Invalid URI character is found in a named tag handle.");
        }

        const std::size_t invalid_char_pos = tag_uri.find_first_of("{}[],");
        if (invalid_char_pos != str_view::npos) {
            emit_error("Tag shorthand cannot contain flow indicators({}[],).");
        }

        return tag_name;
    }

    str_view determine_single_quoted_scalar_range() {
        const str_view sv {m_token_begin_itr, m_end_itr};

        std::size_t pos = sv.find('\'');
        while (pos != str_view::npos) {
            FK_YAML_ASSERT(pos < sv.size());
            if FK_YAML_LIKELY (pos == sv.size() - 1 || sv[pos + 1] != '\'') {
                m_cur_itr = m_token_begin_itr + (pos + 1);
                str_view single_quoted_scalar {m_token_begin_itr, pos};
                check_scalar_content(single_quoted_scalar);
                return single_quoted_scalar;
            }

            pos = sv.find('\'', pos + 2);
        }

        m_cur_itr = m_end_itr;
        emit_error("Invalid end of input buffer in a single-quoted scalar token.");
    }

    str_view determine_double_quoted_scalar_range() {
        const str_view sv {m_token_begin_itr, m_end_itr};

        std::size_t pos = sv.find('\"');
        while (pos != str_view::npos) {
            FK_YAML_ASSERT(pos < sv.size());

            bool is_closed = true;
            if FK_YAML_LIKELY (pos > 0) {
                uint32_t backslash_counts = 0;
                const char* p = m_token_begin_itr + (pos - 1);
                do {
                    if (*p-- != '\\') {
                        break;
                    }
                    ++backslash_counts;
                } while (p != m_token_begin_itr);
                is_closed = ((backslash_counts & 1u) == 0);
            }

            if (is_closed) {
                m_cur_itr = m_token_begin_itr + (pos + 1);
                str_view double_quoted_scalar {m_token_begin_itr, pos};
                check_scalar_content(double_quoted_scalar);
                return double_quoted_scalar;
            }

            pos = sv.find('\"', pos + 1);
        }

        m_cur_itr = m_end_itr;
        emit_error("Invalid end of input buffer in a double-quoted scalar token.");
    }

    str_view determine_plain_scalar_range() {
        const str_view sv {m_token_begin_itr, m_end_itr};

        const str_view filter = (m_state & flow_context_bit) ? "\t\n :{}[]," : "\t\n :";
        std::size_t pos = sv.find_first_of(filter);
        if FK_YAML_UNLIKELY (pos == str_view::npos) {
            check_scalar_content(sv);
            m_cur_itr = m_end_itr;
            return sv;
        }

        bool ends_loop = false;
        uint32_t indent = std::numeric_limits<uint32_t>::max();
        do {
            FK_YAML_ASSERT(pos < sv.size());
            switch (sv[pos]) {
            case '\n': {
                if (indent == std::numeric_limits<uint32_t>::max()) {
                    indent = get_current_indent_level(&sv[pos]);
                }

                constexpr str_view space_filter {" \t\n"};
                const std::size_t non_space_pos = sv.find_first_not_of(space_filter, pos);
                const std::size_t last_newline_pos = sv.find_last_of('\n', non_space_pos);
                FK_YAML_ASSERT(last_newline_pos != str_view::npos);

                if (non_space_pos == str_view::npos || non_space_pos - last_newline_pos - 1 <= indent) {
                    ends_loop = true;
                    break;
                }

                pos = non_space_pos;
                break;
            }
            case ' ':
            case '\t':
                if FK_YAML_UNLIKELY (pos == sv.size() - 1) {
                    ends_loop = true;
                    break;
                }

                {
                    const std::size_t next_non_space = sv.find_first_not_of(" \t", pos + 1);
                    if (next_non_space == str_view::npos) {
                        ends_loop = true;
                        break;
                    }

                    const char next_char = sv[next_non_space];
                    switch (next_char) {
                    case '\n':
                    case '#':
                        ends_loop = true;
                        break;
                    case ':':
                        if (next_non_space + 1 < sv.size()) {
                            const char after_colon = sv[next_non_space + 1];
                            ends_loop = (after_colon == ' ' || after_colon == '\t' || after_colon == '\n');
                        }
                        break;
                    case '{':
                    case '}':
                    case '[':
                    case ']':
                    case ',':
                        ends_loop = (m_state & flow_context_bit);
                        break;
                    default:
                        break;
                    }
                }
                break;
            case ':':
                if FK_YAML_LIKELY (pos + 1 < sv.size()) {
                    switch (sv[pos + 1]) {
                    case ' ':
                    case '\t':
                    case '\n':
                        ends_loop = true;
                        break;
                    default:
                        break;
                    }
                }
                break;
            case '{':
            case '}':
            case '[':
            case ']':
            case ',':
                ends_loop = true;
                break;
            default:
                detail::unreachable();
            }

            if (ends_loop) {
                break;
            }

            pos = sv.find_first_of(filter, pos + 1);
        } while (pos != str_view::npos);

        str_view plain_scalar = sv.substr(0, pos);
        check_scalar_content(plain_scalar);
        m_cur_itr = plain_scalar.end();
        return plain_scalar;
    }

    str_view determine_block_scalar_content_range(
        uint32_t base_indent, uint32_t indicated_indent, uint32_t& content_indent) {
        const str_view sv {m_token_begin_itr, m_end_itr};
        const std::size_t remain_input_len = sv.size();

        uint32_t cur_indent = 0;
        uint32_t max_leading_indent = 0;
        const char* cur_itr = m_token_begin_itr;
        bool stop_increment = false;

        while (cur_itr != m_end_itr) {
            switch (*cur_itr++) {
            case ' ':
                if FK_YAML_LIKELY (!stop_increment) {
                    ++cur_indent;
                }
                continue;
            case '\t':
                stop_increment = true;
                continue;
            case '\n':
                max_leading_indent = std::max(cur_indent, max_leading_indent);
                cur_indent = 0;
                stop_increment = false;
                continue;
            default:
                break;
            }
            break;
        }

        if FK_YAML_UNLIKELY (cur_itr == m_end_itr) {
            m_cur_itr = m_end_itr;

            content_indent =
                indicated_indent == 0 ? std::max(cur_indent, max_leading_indent) : base_indent + indicated_indent;
            return sv;
        }

        if FK_YAML_UNLIKELY (cur_indent < max_leading_indent) {
            emit_error("Any leading empty line must not be more indented than the first non-empty line.");
        }

        if (indicated_indent == 0) {
            FK_YAML_ASSERT(base_indent < cur_indent);
            indicated_indent = cur_indent - base_indent;
        }
        else if FK_YAML_UNLIKELY (cur_indent < base_indent + indicated_indent) {
            emit_error("The first non-empty line in the block scalar is less indented.");
        }

        std::size_t last_newline_pos = sv.find('\n', cur_itr - m_token_begin_itr + 1);
        if (last_newline_pos == str_view::npos) {
            last_newline_pos = remain_input_len;
        }

        content_indent = base_indent + indicated_indent;
        while (last_newline_pos < remain_input_len) {
            std::size_t cur_line_end_pos = sv.find('\n', last_newline_pos + 1);
            if (cur_line_end_pos == str_view::npos) {
                cur_line_end_pos = remain_input_len;
            }

            const std::size_t cur_line_content_begin_pos = sv.find_first_not_of(' ', last_newline_pos + 1);
            if (cur_line_content_begin_pos == str_view::npos) {
                last_newline_pos = cur_line_end_pos;
                continue;
            }

            FK_YAML_ASSERT(last_newline_pos < cur_line_content_begin_pos);
            cur_indent = static_cast<uint32_t>(cur_line_content_begin_pos - last_newline_pos - 1);
            if (cur_indent < content_indent && sv[cur_line_content_begin_pos] != '\n') {
                if FK_YAML_UNLIKELY (cur_indent > base_indent) {
                    emit_error("A content line of the block scalar is less indented.");
                }

                break;
            }

            last_newline_pos = cur_line_end_pos;
        }

        if (last_newline_pos < remain_input_len) {
            ++last_newline_pos;
        }

        m_cur_itr = m_token_begin_itr + last_newline_pos;
        return sv.substr(0, last_newline_pos);
    }

    void check_scalar_content(const str_view& scalar) const {
        const char* p_current = scalar.begin();
        const char* p_end = scalar.end();

        while (p_current != p_end) {
            const uint32_t num_bytes = utf8::get_num_bytes(static_cast<uint8_t>(*p_current));
            if (num_bytes > 1) {
                p_current += num_bytes;
                continue;
            }

            switch (*p_current++) {
            case 0x01:
                emit_error("Control character U+0001 (SOH) must be escaped to \\u0001.");
            case 0x02:
                emit_error("Control character U+0002 (STX) must be escaped to \\u0002.");
            case 0x03:
                emit_error("Control character U+0003 (ETX) must be escaped to \\u0003.");
            case 0x04:
                emit_error("Control character U+0004 (EOT) must be escaped to \\u0004.");
            case 0x05:
                emit_error("Control character U+0005 (ENQ) must be escaped to \\u0005.");
            case 0x06:
                emit_error("Control character U+0006 (ACK) must be escaped to \\u0006.");
            case 0x07:
                emit_error("Control character U+0007 (BEL) must be escaped to \\a or \\u0007.");
            case 0x08:
                emit_error("Control character U+0008 (BS) must be escaped to \\b or \\u0008.");
            case 0x09:
                break;
            case 0x0B:
                emit_error("Control character U+000B (VT) must be escaped to \\v or \\u000B.");
            case 0x0C:
                emit_error("Control character U+000C (FF) must be escaped to \\f or \\u000C.");
            case 0x0E:
                emit_error("Control character U+000E (SO) must be escaped to \\u000E.");
            case 0x0F:
                emit_error("Control character U+000F (SI) must be escaped to \\u000F.");
            case 0x10:
                emit_error("Control character U+0010 (DLE) must be escaped to \\u0010.");
            case 0x11:
                emit_error("Control character U+0011 (DC1) must be escaped to \\u0011.");
            case 0x12:
                emit_error("Control character U+0012 (DC2) must be escaped to \\u0012.");
            case 0x13:
                emit_error("Control character U+0013 (DC3) must be escaped to \\u0013.");
            case 0x14:
                emit_error("Control character U+0014 (DC4) must be escaped to \\u0014.");
            case 0x15:
                emit_error("Control character U+0015 (NAK) must be escaped to \\u0015.");
            case 0x16:
                emit_error("Control character U+0016 (SYN) must be escaped to \\u0016.");
            case 0x17:
                emit_error("Control character U+0017 (ETB) must be escaped to \\u0017.");
            case 0x18:
                emit_error("Control character U+0018 (CAN) must be escaped to \\u0018.");
            case 0x19:
                emit_error("Control character U+0019 (EM) must be escaped to \\u0019.");
            case 0x1A:
                emit_error("Control character U+001A (SUB) must be escaped to \\u001A.");
            case 0x1B:
                emit_error("Control character U+001B (ESC) must be escaped to \\e or \\u001B.");
            case 0x1C:
                emit_error("Control character U+001C (FS) must be escaped to \\u001C.");
            case 0x1D:
                emit_error("Control character U+001D (GS) must be escaped to \\u001D.");
            case 0x1E:
                emit_error("Control character U+001E (RS) must be escaped to \\u001E.");
            case 0x1F:
                emit_error("Control character U+001F (US) must be escaped to \\u001F.");
            default:
                break;
            }
        }
    }

    block_scalar_header convert_to_block_scalar_header(str_view line) {
        constexpr str_view comment_prefix {" #"};
        const std::size_t comment_begin_pos = line.find(comment_prefix);
        if (comment_begin_pos != str_view::npos) {
            line = line.substr(0, comment_begin_pos);
        }

        if (line.empty()) {
            return {};
        }

        block_scalar_header header {};
        for (const char c : line) {
            switch (c) {
            case '-':
                if FK_YAML_UNLIKELY (header.chomp != chomping_indicator_t::CLIP) {
                    emit_error("Too many block chomping indicators specified.");
                }
                header.chomp = chomping_indicator_t::STRIP;
                break;
            case '+':
                if FK_YAML_UNLIKELY (header.chomp != chomping_indicator_t::CLIP) {
                    emit_error("Too many block chomping indicators specified.");
                }
                header.chomp = chomping_indicator_t::KEEP;
                break;
            case '0':
                emit_error("An indentation level for a block scalar cannot be 0.");
            case '1':
            case '2':
            case '3':
            case '4':
            case '5':
            case '6':
            case '7':
            case '8':
            case '9':
                if FK_YAML_UNLIKELY (header.indent > 0) {
                    emit_error("Invalid indentation level for a block scalar. It must be between 1 and 9.");
                }
                header.indent = static_cast<uint32_t>(c - '0');
                break;
            case ' ':
            case '\t':
                break;
            default:
                emit_error("Invalid character found in a block scalar header.");
            }
        }

        return header;
    }

    void skip_white_spaces() {
        m_cur_itr = std::find_if_not(m_cur_itr, m_end_itr, [](char c) { return (c == ' ' || c == '\t'); });
    }

    void skip_white_spaces_and_newline_codes() {
        if (m_cur_itr != m_end_itr) {
            m_cur_itr = std::find_if_not(m_cur_itr, m_end_itr, [](char c) {
                switch (c) {
                case ' ':
                case '\t':
                case '\n':
                    return true;
                default:
                    return false;
                }
            });
        }
    }

    void skip_until_line_end() {
        while (m_cur_itr != m_end_itr) {
            switch (*m_cur_itr) {
            case '\n':
                ++m_cur_itr;
                return;
            default:
                ++m_cur_itr;
                break;
            }
        }
    }

    [[noreturn]] void emit_error(const char* msg) const {
        m_pos_tracker.update_position(m_cur_itr);
        throw fkyaml::parse_error(msg, m_pos_tracker.get_lines_read(), m_pos_tracker.get_cur_pos_in_line());
    }

private:
    const char* m_begin_itr {};
    const char* m_cur_itr {};
    const char* m_token_begin_itr {};
    const char* m_end_itr {};
    mutable position_tracker m_pos_tracker {};
    str_view m_yaml_version;
    str_view m_tag_handle;
    str_view m_tag_prefix;
    block_scalar_header m_block_scalar_header {};
    uint32_t m_last_token_begin_pos {0};
    uint32_t m_last_token_begin_line {0};
    uint32_t m_state {0};
};

FK_YAML_DETAIL_NAMESPACE_END

#endif

#ifndef FK_YAML_DETAIL_INPUT_SCALAR_PARSER_HPP
#define FK_YAML_DETAIL_INPUT_SCALAR_PARSER_HPP

#ifndef FK_YAML_CONVERSIONS_SCALAR_CONV_HPP
#define FK_YAML_CONVERSIONS_SCALAR_CONV_HPP

#include <cmath>
#include <cstdint>
#include <cstring>
#include <limits>

#if FK_YAML_HAS_TO_CHARS
#include <charconv>
#else
#include <string>
#endif

FK_YAML_DETAIL_NAMESPACE_BEGIN

template <std::size_t NumBytes>
struct conv_limits_base {};

template <>
struct conv_limits_base<1u> {
    static constexpr std::size_t max_chars_oct = 3;
    static constexpr std::size_t max_chars_hex = 2;

    static bool check_if_octs_safe(const char* octs, std::size_t len) noexcept {
        return (len < max_chars_oct) || (len == max_chars_oct && octs[0] <= '3');
    }

    static bool check_if_hexs_safe(const char*, std::size_t len) noexcept {
        return len <= max_chars_hex;
    }
};

template <>
struct conv_limits_base<2u> {
    static constexpr std::size_t max_chars_oct = 6;
    static constexpr std::size_t max_chars_hex = 4;

    static bool check_if_octs_safe(const char* octs, std::size_t len) noexcept {
        return (len < max_chars_oct) || (len == max_chars_oct && octs[0] <= '1');
    }

    static bool check_if_hexs_safe(const char*, std::size_t len) noexcept {
        return len <= max_chars_hex;
    }
};

template <>
struct conv_limits_base<4u> {
    static constexpr std::size_t max_chars_oct = 11;
    static constexpr std::size_t max_chars_hex = 8;

    static bool check_if_octs_safe(const char* octs, std::size_t len) noexcept {
        return (len < max_chars_oct) || (len == max_chars_oct && octs[0] <= '3');
    }

    static bool check_if_hexs_safe(const char*, std::size_t len) noexcept {
        return len <= max_chars_hex;
    }
};

template <>
struct conv_limits_base<8u> {
    static constexpr std::size_t max_chars_oct = 22;
    static constexpr std::size_t max_chars_hex = 16;

    static bool check_if_octs_safe(const char* octs, std::size_t len) noexcept {
        return (len < max_chars_oct) || (len == max_chars_oct && octs[0] <= '1');
    }

    static bool check_if_hexs_safe(const char*, std::size_t len) noexcept {
        return len <= max_chars_hex;
    }
};

template <std::size_t NumBytes, bool IsSigned>
struct conv_limits {};

template <>
struct conv_limits<1u, true> : conv_limits_base<1u> {
    static constexpr bool is_signed = true;

    static constexpr std::size_t max_chars_dec = 3;

    static const char* max_value_chars_dec() noexcept {
        static constexpr char max_value_chars[] = "127";
        return &max_value_chars[0];
    }

    static const char* min_value_chars_dec() noexcept {
        static constexpr char min_value_chars[] = "128";
        return &min_value_chars[0];
    }
};

template <>
struct conv_limits<1u, false> : conv_limits_base<1u> {
    static constexpr bool is_signed = false;

    static constexpr std::size_t max_chars_dec = 3;

    static const char* max_value_chars_dec() noexcept {
        static constexpr char max_value_chars[] = "255";
        return &max_value_chars[0];
    }

    static const char* min_value_chars_dec() noexcept {
        static constexpr char min_value_chars[] = "0";
        return &min_value_chars[0];
    }
};

template <>
struct conv_limits<2u, true> : conv_limits_base<2u> {
    static constexpr bool is_signed = true;

    static constexpr std::size_t max_chars_dec = 5;

    static const char* max_value_chars_dec() noexcept {
        static constexpr char max_value_chars[] = "32767";
        return &max_value_chars[0];
    }

    static const char* min_value_chars_dec() noexcept {
        static constexpr char min_value_chars[] = "32768";
        return &min_value_chars[0];
    }
};

template <>
struct conv_limits<2u, false> : conv_limits_base<2u> {
    static constexpr bool is_signed = false;

    static constexpr std::size_t max_chars_dec = 5;

    static const char* max_value_chars_dec() noexcept {
        static constexpr char max_value_chars[] = "65535";
        return &max_value_chars[0];
    }

    static const char* min_value_chars_dec() noexcept {
        static constexpr char min_value_chars[] = "0";
        return &min_value_chars[0];
    }
};

template <>
struct conv_limits<4u, true> : conv_limits_base<4u> {
    static constexpr bool is_signed = true;

    static constexpr std::size_t max_chars_dec = 10;

    static const char* max_value_chars_dec() noexcept {
        static constexpr char max_value_chars[] = "2147483647";
        return &max_value_chars[0];
    }

    static const char* min_value_chars_dec() noexcept {
        static constexpr char min_value_chars[] = "2147483648";
        return &min_value_chars[0];
    }
};

template <>
struct conv_limits<4u, false> : conv_limits_base<4u> {
    static constexpr bool is_signed = false;

    static constexpr std::size_t max_chars_dec = 10;

    static const char* max_value_chars_dec() noexcept {
        static constexpr char max_value_chars[] = "4294967295";
        return &max_value_chars[0];
    }

    static const char* min_value_chars_dec() noexcept {
        static constexpr char min_value_chars[] = "0";
        return &min_value_chars[0];
    }
};

template <>
struct conv_limits<8u, true> : conv_limits_base<8u> {
    static constexpr bool is_signed = true;

    static constexpr std::size_t max_chars_dec = 19;

    static const char* max_value_chars_dec() noexcept {
        static constexpr char max_value_chars[] = "9223372036854775807";
        return &max_value_chars[0];
    }

    static const char* min_value_chars_dec() noexcept {
        static constexpr char min_value_chars[] = "9223372036854775808";
        return &min_value_chars[0];
    }
};

template <>
struct conv_limits<8u, false> : conv_limits_base<8u> {
    static constexpr bool is_signed = false;

    static constexpr std::size_t max_chars_dec = 20;

    static const char* max_value_chars_dec() noexcept {
        static constexpr char max_value_chars[] = "18446744073709551615";
        return &max_value_chars[0];
    }

    static const char* min_value_chars_dec() noexcept {
        static constexpr char min_value_chars[] = "0";
        return &min_value_chars[0];
    }
};

template <typename CharItr>
inline bool aton(CharItr begin, CharItr end, std::nullptr_t&) noexcept {
    static_assert(is_iterator_of<CharItr, char>::value, "aton() accepts iterators for char type");

    if FK_YAML_UNLIKELY (begin == end) {
        return false;
    }

    const auto len = static_cast<uint32_t>(std::distance(begin, end));

    if FK_YAML_LIKELY (len == 4) {
        const char* p_begin = &*begin;
        return (std::strncmp(p_begin, "null", 4) == 0) || (std::strncmp(p_begin, "Null", 4) == 0) ||
               (std::strncmp(p_begin, "NULL", 4) == 0);
    }

    if (len == 1) {
        return *begin == '~';
    }

    return false;
}

template <typename CharItr, typename BoolType>
inline bool atob(CharItr begin, CharItr end, BoolType& boolean) noexcept {
    static_assert(is_iterator_of<CharItr, char>::value, "atob() accepts iterators for char type");

    if FK_YAML_UNLIKELY (begin == end) {
        return false;
    }

    const auto len = static_cast<uint32_t>(std::distance(begin, end));
    const char* p_begin = &*begin;

    if (len == 4) {
        const bool is_true = (std::strncmp(p_begin, "true", 4) == 0) || (std::strncmp(p_begin, "True", 4) == 0) ||
                             (std::strncmp(p_begin, "TRUE", 4) == 0);

        if FK_YAML_LIKELY (is_true) {
            boolean = static_cast<BoolType>(true);
        }
        return is_true;
    }

    if (len == 5) {
        const bool is_false = (std::strncmp(p_begin, "false", 5) == 0) || (std::strncmp(p_begin, "False", 5) == 0) ||
                              (std::strncmp(p_begin, "FALSE", 5) == 0);

        if FK_YAML_LIKELY (is_false) {
            boolean = static_cast<BoolType>(false);
        }
        return is_false;
    }

    return false;
}

template <typename IntType>
inline bool atoi_dec_unchecked(const char* p_begin, const char* p_end, IntType& i) noexcept {
    static_assert(
        is_non_bool_integral<IntType>::value,
        "atoi_dec_unchecked() accepts non-boolean integral types as an output type");

    i = 0;
    do {
        const char c = *p_begin;
        if FK_YAML_UNLIKELY (c < '0' || '9' < c) {
            return false;
        }
        i = i * static_cast<IntType>(10) + static_cast<IntType>(c - '0');
    } while (++p_begin != p_end);

    return true;
}

template <typename IntType>
inline bool atoi_dec_pos(const char* p_begin, const char* p_end, IntType& i) noexcept {
    static_assert(
        is_non_bool_integral<IntType>::value, "atoi_dec_pos() accepts non-boolean integral types as an output type");

    if FK_YAML_UNLIKELY (p_begin == p_end) {
        return false;
    }

    using conv_limits_type = conv_limits<sizeof(IntType), std::is_signed<IntType>::value>;

    const auto len = static_cast<std::size_t>(p_end - p_begin);
    if FK_YAML_UNLIKELY (len > conv_limits_type::max_chars_dec) {
        return false;
    }

    if (len == conv_limits_type::max_chars_dec) {
        const char* p_max_value_chars_dec = conv_limits_type::max_value_chars_dec();

        for (std::size_t idx = 0; idx < conv_limits_type::max_chars_dec; idx++) {
            if (p_begin[idx] < p_max_value_chars_dec[idx]) {
                break;
            }

            if FK_YAML_UNLIKELY (p_begin[idx] > p_max_value_chars_dec[idx]) {
                return false;
            }
        }
    }

    return atoi_dec_unchecked(p_begin, p_end, i);
}

template <typename IntType>
inline bool atoi_dec_neg(const char* p_begin, const char* p_end, IntType& i) noexcept {
    static_assert(
        is_non_bool_integral<IntType>::value, "atoi_dec_neg() accepts non-boolean integral types as an output type");

    if FK_YAML_UNLIKELY (p_begin == p_end) {
        return false;
    }

    using conv_limits_type = conv_limits<sizeof(IntType), std::is_signed<IntType>::value>;

    const auto len = static_cast<std::size_t>(p_end - p_begin);
    if FK_YAML_UNLIKELY (len > conv_limits_type::max_chars_dec) {
        return false;
    }

    if (len == conv_limits_type::max_chars_dec) {
        const char* p_min_value_chars_dec = conv_limits_type::min_value_chars_dec();

        for (std::size_t idx = 0; idx < conv_limits_type::max_chars_dec; idx++) {
            if (p_begin[idx] < p_min_value_chars_dec[idx]) {
                break;
            }

            if FK_YAML_UNLIKELY (p_begin[idx] > p_min_value_chars_dec[idx]) {
                return false;
            }
        }
    }

    return atoi_dec_unchecked(p_begin, p_end, i);
}

template <typename IntType>
inline bool atoi_oct(const char* p_begin, const char* p_end, IntType& i) noexcept {
    static_assert(
        is_non_bool_integral<IntType>::value, "atoi_oct() accepts non-boolean integral types as an output type");

    if FK_YAML_UNLIKELY (p_begin == p_end) {
        return false;
    }

    using conv_limits_type = conv_limits<sizeof(IntType), std::is_signed<IntType>::value>;

    const auto len = static_cast<std::size_t>(p_end - p_begin);
    if FK_YAML_UNLIKELY (!conv_limits_type::check_if_octs_safe(p_begin, len)) {
        return false;
    }

    i = 0;
    do {
        const char c = *p_begin;
        if FK_YAML_UNLIKELY (c < '0' || '7' < c) {
            return false;
        }
        i = i * static_cast<IntType>(8) + static_cast<IntType>(c - '0');
    } while (++p_begin != p_end);

    return true;
}

template <typename IntType>
inline bool atoi_hex(const char* p_begin, const char* p_end, IntType& i) noexcept {
    static_assert(
        is_non_bool_integral<IntType>::value, "atoi_hex() accepts non-boolean integral types as an output type");

    if FK_YAML_UNLIKELY (p_begin == p_end) {
        return false;
    }

    using conv_limits_type = conv_limits<sizeof(IntType), std::is_signed<IntType>::value>;

    const auto len = static_cast<std::size_t>(p_end - p_begin);
    if FK_YAML_UNLIKELY (!conv_limits_type::check_if_hexs_safe(p_begin, len)) {
        return false;
    }

    i = 0;
    do {
        const char c = *p_begin;
        IntType ci = 0;
        if ('0' <= c && c <= '9') {
            ci = static_cast<IntType>(c - '0');
        }
        else if ('A' <= c && c <= 'F') {
            ci = static_cast<IntType>(c - 'A' + 10);
        }
        else if ('a' <= c && c <= 'f') {
            ci = static_cast<IntType>(c - 'a' + 10);
        }
        else {
            return false;
        }
        i = i * static_cast<IntType>(16) + ci;
    } while (++p_begin != p_end);

    return true;
}

template <typename CharItr, typename IntType>
inline bool atoi(CharItr begin, CharItr end, IntType& i) noexcept {
    static_assert(is_iterator_of<CharItr, char>::value, "atoi() accepts iterators for char type");
    static_assert(is_non_bool_integral<IntType>::value, "atoi() accepts non-boolean integral types as an output type");

    if FK_YAML_UNLIKELY (begin == end) {
        return false;
    }

    const auto len = static_cast<uint32_t>(std::distance(begin, end));
    const char* p_begin = &*begin;
    const char* p_end = p_begin + len;

    const char first = *begin;
    if (first == '+') {
        return atoi_dec_pos(p_begin + 1, p_end, i);
    }

    if (first == '-') {
        if (!std::numeric_limits<IntType>::is_signed) {
            return false;
        }

        const bool success = atoi_dec_neg(p_begin + 1, p_end, i);
        if (success) {
            i *= static_cast<IntType>(-1);
        }

        return success;
    }

    if (first != '0') {
        return atoi_dec_pos(p_begin, p_end, i);
    }

    if (p_begin + 1 != p_end) {
        switch (*(p_begin + 1)) {
        case 'o':
            return atoi_oct(p_begin + 2, p_end, i);
        case 'x':
            return atoi_hex(p_begin + 2, p_end, i);
        default:
            return false;
        }
    }

    i = 0;
    return true;
}

inline void set_infinity(float& f, const float sign) noexcept {
    f = std::numeric_limits<float>::infinity() * sign;
}

inline void set_infinity(double& f, const double sign) noexcept {
    f = std::numeric_limits<double>::infinity() * sign;
}

inline void set_nan(float& f) noexcept {
    f = std::nanf("");
}

inline void set_nan(double& f) noexcept {
    f = std::nan("");
}

#if FK_YAML_HAS_TO_CHARS

template <typename FloatType>
inline bool atof_impl(const char* p_begin, const char* p_end, FloatType& f) noexcept {
    static_assert(std::is_floating_point_v<FloatType>, "atof_impl() accepts floating point types as an output type");
    if (auto [ptr, ec] = std::from_chars(p_begin, p_end, f); ec == std::errc {}) {
        return ptr == p_end;
    }
    return false;
}

#else

inline bool atof_impl(const char* p_begin, const char* p_end, float& f) {
    std::size_t idx = 0;
    f = std::stof(std::string(p_begin, p_end), &idx);
    return idx == static_cast<std::size_t>(p_end - p_begin);
}

inline bool atof_impl(const char* p_begin, const char* p_end, double& f) {
    std::size_t idx = 0;
    f = std::stod(std::string(p_begin, p_end), &idx);
    return idx == static_cast<std::size_t>(p_end - p_begin);
}

#endif

template <typename CharItr, typename FloatType>
inline bool atof(CharItr begin, CharItr end, FloatType& f) noexcept(noexcept(atof_impl(&*begin, &*begin, f))) {
    static_assert(is_iterator_of<CharItr, char>::value, "atof() accepts iterators for char type");
    static_assert(std::is_floating_point<FloatType>::value, "atof() accepts floating point types as an output type");

    if FK_YAML_UNLIKELY (begin == end) {
        return false;
    }

    const auto len = static_cast<uint32_t>(std::distance(begin, end));
    const char* p_begin = &*begin;
    const char* p_end = p_begin + len;

    if (*p_begin == '-' || *p_begin == '+') {
        if (len == 5) {
            const char* p_from_second = p_begin + 1;
            const bool is_inf = (std::strncmp(p_from_second, ".inf", 4) == 0) ||
                                (std::strncmp(p_from_second, ".Inf", 4) == 0) ||
                                (std::strncmp(p_from_second, ".INF", 4) == 0);
            if (is_inf) {
                set_infinity(f, *p_begin == '-' ? static_cast<FloatType>(-1.) : static_cast<FloatType>(1.));
                return true;
            }
        }

        if (*p_begin == '+') {
            ++p_begin;
        }
    }
    else if (len == 4) {
        const bool is_inf = (std::strncmp(p_begin, ".inf", 4) == 0) || (std::strncmp(p_begin, ".Inf", 4) == 0) ||
                            (std::strncmp(p_begin, ".INF", 4) == 0);
        if (is_inf) {
            set_infinity(f, static_cast<FloatType>(1.));
            return true;
        }

        const bool is_nan = (std::strncmp(p_begin, ".nan", 4) == 0) || (std::strncmp(p_begin, ".NaN", 4) == 0) ||
                            (std::strncmp(p_begin, ".NAN", 4) == 0);
        if (is_nan) {
            set_nan(f);
            return true;
        }
    }

#if FK_YAML_HAS_TO_CHARS
    return atof_impl(p_begin, p_end, f);
#else
    bool success = false;
    try {
        success = atof_impl(p_begin, p_end, f);
    }
    catch (const std::exception&) {
        success = false;
    }

    return success;
#endif
}

FK_YAML_DETAIL_NAMESPACE_END

#endif

#ifndef FK_YAML_DETAIL_ENCODINGS_YAML_ESCAPER_HPP
#define FK_YAML_DETAIL_ENCODINGS_YAML_ESCAPER_HPP

#include <string>

FK_YAML_DETAIL_NAMESPACE_BEGIN

class yaml_escaper {
    using iterator = ::std::string::const_iterator;

public:
    static bool unescape(const char*& begin, const char* end, std::string& buff) {
        FK_YAML_ASSERT(*begin == '\\' && std::distance(begin, end) > 0);
        bool ret = true;

        switch (*++begin) {
        case 'a':
            buff.push_back('\a');
            break;
        case 'b':
            buff.push_back('\b');
            break;
        case 't':
        case '\t':
            buff.push_back('\t');
            break;
        case 'n':
            buff.push_back('\n');
            break;
        case 'v':
            buff.push_back('\v');
            break;
        case 'f':
            buff.push_back('\f');
            break;
        case 'r':
            buff.push_back('\r');
            break;
        case 'e':
            buff.push_back(static_cast<char>(0x1B));
            break;
        case ' ':
            buff.push_back(' ');
            break;
        case '\"':
            buff.push_back('\"');
            break;
        case '/':
            buff.push_back('/');
            break;
        case '\\':
            buff.push_back('\\');
            break;
        case 'N':
            unescape_escaped_unicode(0x85u, buff);
            break;
        case '_':
            unescape_escaped_unicode(0xA0u, buff);
            break;
        case 'L':
            unescape_escaped_unicode(0x2028u, buff);
            break;
        case 'P':
            unescape_escaped_unicode(0x2029u, buff);
            break;
        case 'x': {
            char32_t codepoint {0};
            ret = extract_codepoint(begin, end, 1, codepoint);
            if FK_YAML_LIKELY (ret) {
                unescape_escaped_unicode(codepoint, buff);
            }
            break;
        }
        case 'u': {
            char32_t codepoint {0};
            ret = extract_codepoint(begin, end, 2, codepoint);
            if FK_YAML_LIKELY (ret) {
                unescape_escaped_unicode(codepoint, buff);
            }
            break;
        }
        case 'U': {
            char32_t codepoint {0};
            ret = extract_codepoint(begin, end, 4, codepoint);
            if FK_YAML_LIKELY (ret) {
                unescape_escaped_unicode(codepoint, buff);
            }
            break;
        }
        default:
            ret = false;
            break;
        }

        return ret;
    }

    static ::std::string escape(const char* begin, const char* end, bool& is_escaped) {
        ::std::string escaped {};
        escaped.reserve(std::distance(begin, end));
        for (; begin != end; ++begin) {
            switch (*begin) {
            case 0x01:
                escaped += "\\u0001";
                is_escaped = true;
                break;
            case 0x02:
                escaped += "\\u0002";
                is_escaped = true;
                break;
            case 0x03:
                escaped += "\\u0003";
                is_escaped = true;
                break;
            case 0x04:
                escaped += "\\u0004";
                is_escaped = true;
                break;
            case 0x05:
                escaped += "\\u0005";
                is_escaped = true;
                break;
            case 0x06:
                escaped += "\\u0006";
                is_escaped = true;
                break;
            case '\a':
                escaped += "\\a";
                is_escaped = true;
                break;
            case '\b':
                escaped += "\\b";
                is_escaped = true;
                break;
            case '\t':
                escaped += "\\t";
                is_escaped = true;
                break;
            case '\n':
                escaped += "\\n";
                is_escaped = true;
                break;
            case '\v':
                escaped += "\\v";
                is_escaped = true;
                break;
            case '\f':
                escaped += "\\f";
                is_escaped = true;
                break;
            case '\r':
                escaped += "\\r";
                is_escaped = true;
                break;
            case 0x0E:
                escaped += "\\u000E";
                is_escaped = true;
                break;
            case 0x0F:
                escaped += "\\u000F";
                is_escaped = true;
                break;
            case 0x10:
                escaped += "\\u0010";
                is_escaped = true;
                break;
            case 0x11:
                escaped += "\\u0011";
                is_escaped = true;
                break;
            case 0x12:
                escaped += "\\u0012";
                is_escaped = true;
                break;
            case 0x13:
                escaped += "\\u0013";
                is_escaped = true;
                break;
            case 0x14:
                escaped += "\\u0014";
                is_escaped = true;
                break;
            case 0x15:
                escaped += "\\u0015";
                is_escaped = true;
                break;
            case 0x16:
                escaped += "\\u0016";
                is_escaped = true;
                break;
            case 0x17:
                escaped += "\\u0017";
                is_escaped = true;
                break;
            case 0x18:
                escaped += "\\u0018";
                is_escaped = true;
                break;
            case 0x19:
                escaped += "\\u0019";
                is_escaped = true;
                break;
            case 0x1A:
                escaped += "\\u001A";
                is_escaped = true;
                break;
            case 0x1B:
                escaped += "\\e";
                is_escaped = true;
                break;
            case 0x1C:
                escaped += "\\u001C";
                is_escaped = true;
                break;
            case 0x1D:
                escaped += "\\u001D";
                is_escaped = true;
                break;
            case 0x1E:
                escaped += "\\u001E";
                is_escaped = true;
                break;
            case 0x1F:
                escaped += "\\u001F";
                is_escaped = true;
                break;
            case '\"':
                escaped += "\\\"";
                is_escaped = true;
                break;
            case '\\':
                escaped += "\\\\";
                is_escaped = true;
                break;
            default:
                const std::ptrdiff_t diff = static_cast<int>(std::distance(begin, end));
                if (diff > 1) {
                    if (*begin == static_cast<char>(0xC2u) && *(begin + 1) == static_cast<char>(0x85u)) {
                        escaped += "\\N";
                        std::advance(begin, 1);
                        is_escaped = true;
                        break;
                    }
                    if (*begin == static_cast<char>(0xC2u) && *(begin + 1) == static_cast<char>(0xA0u)) {
                        escaped += "\\_";
                        std::advance(begin, 1);
                        is_escaped = true;
                        break;
                    }

                    if (diff > 2) {
                        if (*begin == static_cast<char>(0xE2u) && *(begin + 1) == static_cast<char>(0x80u) &&
                            *(begin + 2) == static_cast<char>(0xA8u)) {
                            escaped += "\\L";
                            std::advance(begin, 2);
                            is_escaped = true;
                            break;
                        }
                        if (*begin == static_cast<char>(0xE2u) && *(begin + 1) == static_cast<char>(0x80u) &&
                            *(begin + 2) == static_cast<char>(0xA9u)) {
                            escaped += "\\P";
                            std::advance(begin, 2);
                            is_escaped = true;
                            break;
                        }
                    }
                }
                escaped += *begin;
                break;
            }
        }
        return escaped;
    }

private:
    static bool convert_hexchar_to_byte(char source, uint8_t& byte) {
        if ('0' <= source && source <= '9') {
            byte = static_cast<uint8_t>(source - '0');
            return true;
        }

        if ('A' <= source && source <= 'F') {
            byte = static_cast<uint8_t>(source - 'A' + 10);
            return true;
        }

        if ('a' <= source && source <= 'f') {
            byte = static_cast<uint8_t>(source - 'a' + 10);
            return true;
        }

        return false;
    }

    static bool extract_codepoint(const char*& begin, const char* end, int bytes_to_read, char32_t& codepoint) {
        const bool has_enough_room = static_cast<int>(std::distance(begin, end)) >= (bytes_to_read - 1);
        if (!has_enough_room) {
            return false;
        }

        const int read_size = bytes_to_read * 2;
        uint8_t byte {0};
        codepoint = 0;

        for (int i = read_size - 1; i >= 0; i--) {
            const bool is_valid = convert_hexchar_to_byte(*++begin, byte);
            if (!is_valid) {
                return false;
            }
            codepoint |= static_cast<char32_t>(byte << (4 * i));
        }

        return true;
    }

    static void unescape_escaped_unicode(char32_t codepoint, std::string& buff) {
        std::array<uint8_t, 4> encode_buff {{}};
        uint32_t encoded_size {0};
        utf8::from_utf32(codepoint, encode_buff, encoded_size);
        buff.append(reinterpret_cast<char*>(encode_buff.data()), encoded_size);
    }
};

FK_YAML_DETAIL_NAMESPACE_END

#endif

#ifndef FK_YAML_DETAIL_INPUT_SCALAR_SCANNER_HPP
#define FK_YAML_DETAIL_INPUT_SCALAR_SCANNER_HPP

#include <cstring>
#include <string>

FK_YAML_DETAIL_NAMESPACE_BEGIN

class scalar_scanner {
public:
    static node_type scan(const char* begin, const char* end) noexcept {
        if (begin == end) {
            return node_type::STRING;
        }

        const auto len = static_cast<uint32_t>(std::distance(begin, end));
        if (len > 5) {
            return scan_possible_number_token(begin, len);
        }

        const char* p_begin = &*begin;

        switch (len) {
        case 1:
            if (*p_begin == '~') {
                return node_type::NULL_OBJECT;
            }
            break;
        case 4:
            switch (*p_begin) {
            case 'n':
                return (std::strncmp(p_begin + 1, "ull", 3) == 0) ? node_type::NULL_OBJECT : node_type::STRING;
            case 'N':
                return ((std::strncmp(p_begin + 1, "ull", 3) == 0) || (std::strncmp(p_begin + 1, "ULL", 3) == 0))
                           ? node_type::NULL_OBJECT
                           : node_type::STRING;
            case 't':
                return (std::strncmp(p_begin + 1, "rue", 3) == 0) ? node_type::BOOLEAN : node_type::STRING;
            case 'T':
                return ((std::strncmp(p_begin + 1, "rue", 3) == 0) || (std::strncmp(p_begin + 1, "RUE", 3) == 0))
                           ? node_type::BOOLEAN
                           : node_type::STRING;
            case '.': {
                const char* p_from_second = p_begin + 1;
                const bool is_inf_or_nan_scalar =
                    (std::strncmp(p_from_second, "inf", 3) == 0) || (std::strncmp(p_from_second, "Inf", 3) == 0) ||
                    (std::strncmp(p_from_second, "INF", 3) == 0) || (std::strncmp(p_from_second, "nan", 3) == 0) ||
                    (std::strncmp(p_from_second, "NaN", 3) == 0) || (std::strncmp(p_from_second, "NAN", 3) == 0);
                if (is_inf_or_nan_scalar) {
                    return node_type::FLOAT;
                }
                break;
            }
            default:
                break;
            }
            break;
        case 5:
            switch (*p_begin) {
            case 'f':
                return (std::strncmp(p_begin + 1, "alse", 4) == 0) ? node_type::BOOLEAN : node_type::STRING;
            case 'F':
                return ((std::strncmp(p_begin + 1, "alse", 4) == 0) || (std::strncmp(p_begin + 1, "ALSE", 4) == 0))
                           ? node_type::BOOLEAN
                           : node_type::STRING;
            case '+':
            case '-':
                if (*(p_begin + 1) == '.') {
                    const char* p_from_third = p_begin + 2;
                    const bool is_min_inf = (std::strncmp(p_from_third, "inf", 3) == 0) ||
                                            (std::strncmp(p_from_third, "Inf", 3) == 0) ||
                                            (std::strncmp(p_from_third, "INF", 3) == 0);
                    if (is_min_inf) {
                        return node_type::FLOAT;
                    }
                }
                break;
            default:
                break;
            }
            break;
        default:
            break;
        }

        return scan_possible_number_token(begin, len);
    }

private:
    static node_type scan_possible_number_token(const char* itr, uint32_t len) noexcept {
        FK_YAML_ASSERT(len > 0);

        switch (*itr) {
        case '-':
            return (len > 1) ? scan_negative_number(++itr, --len) : node_type::STRING;
        case '+':
            return (len > 1) ? scan_decimal_number(++itr, --len) : node_type::STRING;
        case '.':
            return (len > 1) ? scan_after_decimal_point(++itr, --len) : node_type::STRING;
        case '0':
            return (len > 1) ? scan_after_zero_at_first(++itr, --len) : node_type::INTEGER;
        case '1':
        case '2':
        case '3':
        case '4':
        case '5':
        case '6':
        case '7':
        case '8':
        case '9':
            return (len > 1) ? scan_decimal_number(++itr, --len) : node_type::INTEGER;
        default:
            return node_type::STRING;
        }
    }

    static node_type scan_negative_number(const char* itr, uint32_t len) noexcept {
        FK_YAML_ASSERT(len > 0);

        if (is_digit(*itr)) {
            return (len > 1) ? scan_decimal_number(++itr, --len) : node_type::INTEGER;
        }

        if (*itr == '.') {
            return (len > 1) ? scan_after_decimal_point(++itr, --len) : node_type::STRING;
        }

        return node_type::STRING;
    }

    static node_type scan_after_zero_at_first(const char* itr, uint32_t len) noexcept {
        FK_YAML_ASSERT(len > 0);

        if (is_digit(*itr)) {
            return node_type::STRING;
        }

        switch (*itr) {
        case '.':
            return (len > 1) ? scan_after_decimal_point(++itr, --len) : node_type::FLOAT;
        case 'e':
        case 'E':
            return (len > 1) ? scan_after_exponent(++itr, --len) : node_type::STRING;
        case 'o':
            return (len > 1) ? scan_octal_number(++itr, --len) : node_type::STRING;
        case 'x':
            return (len > 1) ? scan_hexadecimal_number(++itr, --len) : node_type::STRING;
        default:
            return node_type::STRING;
        }
    }

    static node_type scan_decimal_number(const char* itr, uint32_t len) noexcept {
        FK_YAML_ASSERT(len > 0);

        if (is_digit(*itr)) {
            return (len > 1) ? scan_decimal_number(++itr, --len) : node_type::INTEGER;
        }

        switch (*itr) {
        case '.': {
            return (len > 1) ? scan_after_decimal_point(++itr, --len) : node_type::FLOAT;
        }
        case 'e':
        case 'E':
            return (len > 1) ? scan_after_exponent(++itr, --len) : node_type::STRING;
        default:
            return node_type::STRING;
        }
    }

    static node_type scan_after_decimal_point(const char* itr, uint32_t len) noexcept {
        FK_YAML_ASSERT(len > 0);

        for (uint32_t i = 0; i < len; i++) {
            const char c = *itr++;

            if (is_digit(c)) {
                continue;
            }

            if (c == 'e' || c == 'E') {
                if (i == len - 1) {
                    return node_type::STRING;
                }
                return scan_after_exponent(itr, len - i - 1);
            }

            return node_type::STRING;
        }

        return node_type::FLOAT;
    }

    static node_type scan_after_exponent(const char* itr, uint32_t len) noexcept {
        FK_YAML_ASSERT(len > 0);

        const char c = *itr;
        if (c == '+' || c == '-') {
            if (len == 1) {
                return node_type::STRING;
            }
            ++itr;
            --len;
        }

        for (uint32_t i = 0; i < len; i++) {
            if (!is_digit(*itr++)) {
                return node_type::STRING;
            }
        }

        return node_type::FLOAT;
    }

    static node_type scan_octal_number(const char* itr, uint32_t len) noexcept {
        FK_YAML_ASSERT(len > 0);

        switch (*itr) {
        case '0':
        case '1':
        case '2':
        case '3':
        case '4':
        case '5':
        case '6':
        case '7':
            return (len > 1) ? scan_octal_number(++itr, --len) : node_type::INTEGER;
        default:
            return node_type::STRING;
        }
    }

    static node_type scan_hexadecimal_number(const char* itr, uint32_t len) noexcept {
        FK_YAML_ASSERT(len > 0);

        if (is_xdigit(*itr)) {
            return (len > 1) ? scan_hexadecimal_number(++itr, --len) : node_type::INTEGER;
        }
        return node_type::STRING;
    }

    static bool is_digit(char c) {
        return ('0' <= c && c <= '9');
    }

    static bool is_xdigit(char c) {
        return (('0' <= c && c <= '9') || ('A' <= c && c <= 'F') || ('a' <= c && c <= 'f'));
    }
};

FK_YAML_DETAIL_NAMESPACE_END

#endif

#ifndef FK_YAML_DETAIL_INPUT_TAG_T_HPP
#define FK_YAML_DETAIL_INPUT_TAG_T_HPP

#include <cstdint>

FK_YAML_DETAIL_NAMESPACE_BEGIN

enum class tag_t : std::uint8_t {
    NONE,
    NON_SPECIFIC,
    CUSTOM_TAG,
    SEQUENCE,
    MAPPING,
    NULL_VALUE,
    BOOLEAN,
    INTEGER,
    FLOATING_NUMBER,
    STRING,
};

FK_YAML_DETAIL_NAMESPACE_END

#endif

FK_YAML_DETAIL_NAMESPACE_BEGIN

template <typename BasicNodeType>
class scalar_parser {
    static_assert(is_basic_node<BasicNodeType>::value, "scalar_parser only accepts basic_node<...>");

public:
    using basic_node_type = BasicNodeType;

private:
    using boolean_type = typename basic_node_type::boolean_type;
    using integer_type = typename basic_node_type::integer_type;
    using float_number_type = typename basic_node_type::float_number_type;
    using string_type = typename basic_node_type::string_type;

public:
    scalar_parser(uint32_t line, uint32_t indent) noexcept
        : m_line(line),
          m_indent(indent) {
    }

    ~scalar_parser() noexcept = default;

    scalar_parser(const scalar_parser&) = default;
    scalar_parser& operator=(const scalar_parser&) = default;

    scalar_parser(scalar_parser&&) noexcept = default;
    scalar_parser& operator=(scalar_parser&&) noexcept(std::is_nothrow_move_assignable<std::string>::value) = default;

    basic_node_type parse_flow(lexical_token_t lex_type, tag_t tag_type, str_view token) {
        FK_YAML_ASSERT(
            lex_type == lexical_token_t::PLAIN_SCALAR || lex_type == lexical_token_t::SINGLE_QUOTED_SCALAR ||
            lex_type == lexical_token_t::DOUBLE_QUOTED_SCALAR);
        FK_YAML_ASSERT(tag_type != tag_t::SEQUENCE && tag_type != tag_t::MAPPING);

        token = parse_flow_scalar_token(lex_type, token);
        const node_type value_type = decide_value_type(lex_type, tag_type, token);
        return create_scalar_node(value_type, tag_type, token);
    }

    basic_node_type parse_block(
        lexical_token_t lex_type, tag_t tag_type, str_view token, const block_scalar_header& header) {
        FK_YAML_ASSERT(
            lex_type == lexical_token_t::BLOCK_LITERAL_SCALAR || lex_type == lexical_token_t::BLOCK_FOLDED_SCALAR);
        FK_YAML_ASSERT(tag_type != tag_t::SEQUENCE && tag_type != tag_t::MAPPING);

        if (lex_type == lexical_token_t::BLOCK_LITERAL_SCALAR) {
            token = parse_block_literal_scalar(token, header);
        }
        else {
            token = parse_block_folded_scalar(token, header);
        }

        const node_type value_type = decide_value_type(lex_type, tag_type, token);
        return create_scalar_node(value_type, tag_type, token);
    }

private:
    str_view parse_flow_scalar_token(lexical_token_t lex_type, str_view token) {
        switch (lex_type) {
        case lexical_token_t::PLAIN_SCALAR:
            token = parse_plain_scalar(token);
            break;
        case lexical_token_t::SINGLE_QUOTED_SCALAR:
            token = parse_single_quoted_scalar(token);
            break;
        case lexical_token_t::DOUBLE_QUOTED_SCALAR:
            token = parse_double_quoted_scalar(token);
            break;
        default:
            unreachable();
        }

        return token;
    }

    str_view parse_plain_scalar(str_view token) noexcept {
        FK_YAML_ASSERT(!token.empty());

        std::size_t newline_pos = token.find('\n');
        if (newline_pos == str_view::npos) {
            return token;
        }

        m_use_owned_buffer = true;

        if (m_buffer.capacity() < token.size()) {
            m_buffer.reserve(token.size());
        }

        do {
            process_line_folding(token, newline_pos);
            newline_pos = token.find('\n');
        } while (newline_pos != str_view::npos);

        m_buffer.append(token.begin(), token.size());

        return {m_buffer};
    }

    str_view parse_single_quoted_scalar(str_view token) noexcept {
        if (token.empty()) {
            return token;
        }

        constexpr str_view filter {"\'\n"};
        std::size_t pos = token.find_first_of(filter);
        if (pos == str_view::npos) {
            return token;
        }

        m_use_owned_buffer = true;

        if (m_buffer.capacity() < token.size()) {
            m_buffer.reserve(token.size());
        }

        do {
            FK_YAML_ASSERT(pos < token.size());
            FK_YAML_ASSERT(token[pos] == '\'' || token[pos] == '\n');

            if (token[pos] == '\'') {
                FK_YAML_ASSERT(pos + 1 < token.size());
                m_buffer.append(token.begin(), token.begin() + (pos + 1));
                token.remove_prefix(pos + 2);
            }
            else {
                process_line_folding(token, pos);
            }

            pos = token.find_first_of(filter);
        } while (pos != str_view::npos);

        if (!token.empty()) {
            m_buffer.append(token.begin(), token.size());
        }

        return {m_buffer};
    }

    str_view parse_double_quoted_scalar(str_view token) {
        if (token.empty()) {
            return token;
        }

        constexpr str_view filter {"\\\n"};
        std::size_t pos = token.find_first_of(filter);
        if (pos == str_view::npos) {
            return token;
        }

        m_use_owned_buffer = true;

        if (m_buffer.capacity() < token.size()) {
            m_buffer.reserve(token.size());
        }

        do {
            FK_YAML_ASSERT(pos < token.size());
            FK_YAML_ASSERT(token[pos] == '\\' || token[pos] == '\n');

            if (token[pos] == '\\') {
                FK_YAML_ASSERT(pos + 1 < token.size());
                m_buffer.append(token.begin(), token.begin() + pos);

                if (token[pos + 1] != '\n') {
                    token.remove_prefix(pos);
                    const char* p_escape_begin = token.begin();
                    const bool is_valid_escaping = yaml_escaper::unescape(p_escape_begin, token.end(), m_buffer);
                    if FK_YAML_UNLIKELY (!is_valid_escaping) {
                        throw parse_error(
                            "Unsupported escape sequence is found in a double quoted scalar.", m_line, m_indent);
                    }

                    token.remove_prefix((p_escape_begin - token.begin()) + 1);
                }
                else {
                    std::size_t non_space_pos = token.find_first_not_of(" \t", pos + 2);
                    if (non_space_pos == str_view::npos) {
                        non_space_pos = token.size();
                    }
                    token.remove_prefix(non_space_pos);
                }
            }
            else {
                process_line_folding(token, pos);
            }

            pos = token.find_first_of(filter);
        } while (pos != str_view::npos);

        if (!token.empty()) {
            m_buffer.append(token.begin(), token.size());
        }

        return {m_buffer};
    }

    str_view parse_block_literal_scalar(str_view token, const block_scalar_header& header) {
        if FK_YAML_UNLIKELY (token.empty()) {
            return token;
        }

        m_use_owned_buffer = true;
        m_buffer.reserve(token.size());

        std::size_t cur_line_begin_pos = 0;
        do {
            bool has_newline_at_end = true;
            std::size_t cur_line_end_pos = token.find('\n', cur_line_begin_pos);
            if (cur_line_end_pos == str_view::npos) {
                has_newline_at_end = false;
                cur_line_end_pos = token.size();
            }

            const std::size_t line_size = cur_line_end_pos - cur_line_begin_pos;
            const str_view line = token.substr(cur_line_begin_pos, line_size);

            if (line.size() > header.indent) {
                m_buffer.append(line.begin() + header.indent, line.end());
            }

            if (!has_newline_at_end) {
                break;
            }

            m_buffer.push_back('\n');
            cur_line_begin_pos = cur_line_end_pos + 1;
        } while (cur_line_begin_pos < token.size());

        process_chomping(header.chomp);

        return {m_buffer};
    }

    str_view parse_block_folded_scalar(str_view token, const block_scalar_header& header) {
        if FK_YAML_UNLIKELY (token.empty()) {
            return token;
        }

        m_use_owned_buffer = true;
        m_buffer.reserve(token.size());

        constexpr str_view white_space_filter {" \t"};

        std::size_t cur_line_begin_pos = 0;
        bool has_newline_at_end = true;
        bool can_be_folded = false;
        do {
            std::size_t cur_line_end_pos = token.find('\n', cur_line_begin_pos);
            if (cur_line_end_pos == str_view::npos) {
                has_newline_at_end = false;
                cur_line_end_pos = token.size();
            }

            const std::size_t line_size = cur_line_end_pos - cur_line_begin_pos;
            const str_view line = token.substr(cur_line_begin_pos, line_size);
            const bool is_empty = line.find_first_not_of(white_space_filter) == str_view::npos;

            if (line.size() <= header.indent) {
                m_buffer.push_back('\n');
                can_be_folded = false;
            }
            else if (is_empty) {
                m_buffer.push_back('\n');
                m_buffer.append(line.begin() + header.indent, line.end());
                m_buffer.push_back('\n');
            }
            else {
                const std::size_t non_space_pos = line.find_first_not_of(white_space_filter);
                const bool is_more_indented = (non_space_pos != str_view::npos) && (non_space_pos > header.indent);

                if (can_be_folded) {
                    if (is_more_indented) {
                        m_buffer.push_back('\n');
                    }
                    else {
                        m_buffer.push_back(' ');
                    }

                    can_be_folded = false;
                }

                m_buffer.append(line.begin() + header.indent, line.end());

                if (is_more_indented && has_newline_at_end) {
                    m_buffer.push_back('\n');
                }
                else {
                    can_be_folded = true;
                }
            }

            if (!has_newline_at_end) {
                break;
            }

            cur_line_begin_pos = cur_line_end_pos + 1;
        } while (cur_line_begin_pos < token.size());

        if (has_newline_at_end && can_be_folded) {
            m_buffer.push_back('\n');
        }

        process_chomping(header.chomp);

        return {m_buffer};
    }

    void process_chomping(chomping_indicator_t chomp) {
        switch (chomp) {
        case chomping_indicator_t::STRIP: {
            const std::size_t content_end_pos = m_buffer.find_last_not_of('\n');
            if (content_end_pos == std::string::npos) {
                m_buffer.clear();
                break;
            }

            if (content_end_pos == m_buffer.size() - 1) {
                break;
            }

            m_buffer.erase(content_end_pos + 1);

            break;
        }
        case chomping_indicator_t::CLIP: {
            const std::size_t content_end_pos = m_buffer.find_last_not_of('\n');
            if (content_end_pos == std::string::npos) {
                m_buffer.clear();
                break;
            }

            if (content_end_pos == m_buffer.size() - 1) {
                break;
            }

            m_buffer.erase(content_end_pos + 2);

            break;
        }
        case chomping_indicator_t::KEEP:
            break;
        }
    }

    void process_line_folding(str_view& token, std::size_t newline_pos) noexcept {
        const std::size_t last_non_space_pos = token.substr(0, newline_pos + 1).find_last_not_of(" \t");
        if (last_non_space_pos == str_view::npos) {
            m_buffer.append(token.begin(), newline_pos);
        }
        else {
            m_buffer.append(token.begin(), last_non_space_pos + 1);
        }
        token.remove_prefix(newline_pos + 1);

        uint32_t empty_line_counts = 0;
        do {
            const std::size_t non_space_pos = token.find_first_not_of(" \t");
            if (non_space_pos == str_view::npos) {
                token.remove_prefix(token.size());
                break;
            }
            if (token[non_space_pos] != '\n') {
                token.remove_prefix(non_space_pos);
                break;
            }

            token.remove_prefix(non_space_pos + 1);
            ++empty_line_counts;
        } while (true);

        if (empty_line_counts > 0) {
            m_buffer.append(empty_line_counts, '\n');
        }
        else {
            m_buffer.push_back(' ');
        }
    }

    node_type decide_value_type(lexical_token_t lex_type, tag_t tag_type, str_view token) const noexcept {
        node_type value_type {node_type::STRING};
        if (lex_type == lexical_token_t::PLAIN_SCALAR) {
            value_type = scalar_scanner::scan(token.begin(), token.end());
        }

        switch (tag_type) {
        case tag_t::NULL_VALUE:
            value_type = node_type::NULL_OBJECT;
            break;
        case tag_t::BOOLEAN:
            value_type = node_type::BOOLEAN;
            break;
        case tag_t::INTEGER:
            value_type = node_type::INTEGER;
            break;
        case tag_t::FLOATING_NUMBER:
            value_type = node_type::FLOAT;
            break;
        case tag_t::STRING:
        case tag_t::NON_SPECIFIC:
            value_type = node_type::STRING;
            break;
        case tag_t::NONE:
        case tag_t::CUSTOM_TAG:
        default:
            break;
        }

        return value_type;
    }

    basic_node_type create_scalar_node(node_type val_type, tag_t tag_type, str_view token) {
        switch (val_type) {
        case node_type::NULL_OBJECT: {
            std::nullptr_t null = nullptr;
            const bool converted = detail::aton(token.begin(), token.end(), null);
            if FK_YAML_UNLIKELY (!converted) {
                throw parse_error("Failed to convert a scalar to a null.", m_line, m_indent);
            }
            return basic_node_type {};
        }
        case node_type::BOOLEAN: {
            auto boolean = static_cast<boolean_type>(false);
            const bool converted = detail::atob(token.begin(), token.end(), boolean);
            if FK_YAML_UNLIKELY (!converted) {
                throw parse_error("Failed to convert a scalar to a boolean.", m_line, m_indent);
            }
            return basic_node_type(boolean);
        }
        case node_type::INTEGER: {
            integer_type integer = 0;
            const bool converted = detail::atoi(token.begin(), token.end(), integer);
            if FK_YAML_LIKELY (converted) {
                return basic_node_type(integer);
            }
            if FK_YAML_UNLIKELY (tag_type == tag_t::INTEGER) {
                throw parse_error("Failed to convert a scalar to an integer.", m_line, m_indent);
            }

            return basic_node_type(string_type(token.begin(), token.end()));
        }
        case node_type::FLOAT: {
            float_number_type float_val = 0;
            const bool converted = detail::atof(token.begin(), token.end(), float_val);
            if FK_YAML_LIKELY (converted) {
                return basic_node_type(float_val);
            }
            if FK_YAML_UNLIKELY (tag_type == tag_t::FLOATING_NUMBER) {
                throw parse_error("Failed to convert a scalar to a floating point value", m_line, m_indent);
            }

            return basic_node_type(string_type(token.begin(), token.end()));
        }
        case node_type::STRING:
            if (!m_use_owned_buffer) {
                return basic_node_type(string_type(token.begin(), token.end()));
            }
            m_use_owned_buffer = false;
            return basic_node_type(std::move(m_buffer));
        default:
            detail::unreachable();
        }
    }

    uint32_t m_line {0};
    uint32_t m_indent {0};
    bool m_use_owned_buffer {false};
    std::string m_buffer;
};

FK_YAML_DETAIL_NAMESPACE_END

#endif

#ifndef FK_YAML_DETAIL_INPUT_TAG_RESOLVER_HPP
#define FK_YAML_DETAIL_INPUT_TAG_RESOLVER_HPP

#include <memory>
#include <string>

FK_YAML_DETAIL_NAMESPACE_BEGIN

static constexpr str_view default_primary_handle_prefix {"!"};
static constexpr str_view default_secondary_handle_prefix {"tag:yaml.org,2002:"};

template <typename BasicNodeType>
class tag_resolver {
    static_assert(is_basic_node<BasicNodeType>::value, "tag_resolver only accepts basic_node<...>.");
    using doc_metainfo_type = document_metainfo<BasicNodeType>;

public:
    static tag_t resolve_tag(const str_view tag, const std::shared_ptr<doc_metainfo_type>& directives) {
        const std::string normalized = normalize_tag_name(tag, directives);
        return convert_to_tag_type(normalized);
    }

private:
    static std::string normalize_tag_name(const str_view tag, const std::shared_ptr<doc_metainfo_type>& directives) {
        if FK_YAML_UNLIKELY (tag.empty()) {
            throw invalid_tag("tag must not be empty.", "");
        }
        if FK_YAML_UNLIKELY (tag[0] != '!') {
            throw invalid_tag("tag must start with \'!\'", std::string(tag.begin(), tag.end()).c_str());
        }

        if (tag.size() == 1) {
            return {tag.begin(), tag.end()};
        }

        std::string normalized {"!<"};
        switch (tag[1]) {
        case '!': {
            const bool is_null_or_empty = !directives || directives->secondary_handle_prefix.empty();
            if (is_null_or_empty) {
                normalized.append(default_secondary_handle_prefix.begin(), default_secondary_handle_prefix.end());
            }
            else {
                normalized += directives->secondary_handle_prefix;
            }

            const str_view body = tag.substr(2);
            normalized.append(body.begin(), body.end());
            break;
        }
        case '<':
            if (tag[2] == '!') {
                const bool is_null_or_empty = !directives || directives->primary_handle_prefix.empty();
                if (is_null_or_empty) {
                    normalized.append(default_primary_handle_prefix.begin(), default_primary_handle_prefix.end());
                }
                else {
                    normalized += directives->primary_handle_prefix;
                }

                const str_view body = tag.substr(3);
                return normalized.append(body.begin(), body.end());
            }

            return {tag.begin(), tag.end()};
        default: {
            const std::size_t tag_end_pos = tag.find_first_of('!', 1);

            if (tag_end_pos != std::string::npos) {
                FK_YAML_ASSERT(tag_end_pos < tag.size() - 1);

                const bool is_null_or_empty = !directives || directives->named_handle_map.empty();
                if FK_YAML_UNLIKELY (is_null_or_empty) {
                    throw invalid_tag(
                        "named handle has not been registered.", std::string(tag.begin(), tag.end()).c_str());
                }

                const str_view named_handle = tag.substr(0, tag_end_pos + 1);
                auto named_handle_itr = directives->named_handle_map.find({named_handle.begin(), named_handle.end()});
                auto end_itr = directives->named_handle_map.end();
                if FK_YAML_UNLIKELY (named_handle_itr == end_itr) {
                    throw invalid_tag(
                        "named handle has not been registered.", std::string(tag.begin(), tag.end()).c_str());
                }

                normalized += named_handle_itr->second;
                const str_view body = tag.substr(tag_end_pos + 1);
                normalized.append(body.begin(), body.end());
                break;
            }

            const bool is_null_or_empty = !directives || directives->primary_handle_prefix.empty();
            if (is_null_or_empty) {
                normalized.append(default_primary_handle_prefix.begin(), default_primary_handle_prefix.end());
            }
            else {
                normalized += directives->primary_handle_prefix;
            }

            const str_view body = tag.substr(1);
            normalized.append(body.begin(), body.end());
            break;
        }
        }

        normalized += ">";
        return normalized;
    }

    static tag_t convert_to_tag_type(const std::string& normalized) {
        if (normalized == "!") {
            return tag_t::NON_SPECIFIC;
        }

        if (normalized.size() < 24) {
            return tag_t::CUSTOM_TAG;
        }
        if (normalized.rfind("!<tag:yaml.org,2002:", 0) == std::string::npos) {
            return tag_t::CUSTOM_TAG;
        }

        if (normalized == "!<tag:yaml.org,2002:seq>") {
            return tag_t::SEQUENCE;
        }
        if (normalized == "!<tag:yaml.org,2002:map>") {
            return tag_t::MAPPING;
        }
        if (normalized == "!<tag:yaml.org,2002:null>") {
            return tag_t::NULL_VALUE;
        }
        if (normalized == "!<tag:yaml.org,2002:bool>") {
            return tag_t::BOOLEAN;
        }
        if (normalized == "!<tag:yaml.org,2002:int>") {
            return tag_t::INTEGER;
        }
        if (normalized == "!<tag:yaml.org,2002:float>") {
            return tag_t::FLOATING_NUMBER;
        }
        if (normalized == "!<tag:yaml.org,2002:str>") {
            return tag_t::STRING;
        }

        return tag_t::CUSTOM_TAG;
    }
};

FK_YAML_DETAIL_NAMESPACE_END

#endif

#ifndef FK_YAML_DETAIL_META_INPUT_ADAPTER_TRAITS_HPP
#define FK_YAML_DETAIL_META_INPUT_ADAPTER_TRAITS_HPP

#include <type_traits>

FK_YAML_DETAIL_NAMESPACE_BEGIN

template <typename T>
using get_buffer_view_fn_t = decltype(std::declval<T>().get_buffer_view());

template <typename InputAdapterType>
using has_get_buffer_view = is_detected<get_buffer_view_fn_t, InputAdapterType>;

template <typename T, typename = void>
struct is_input_adapter : std::false_type {};

template <typename InputAdapterType>
struct is_input_adapter<InputAdapterType, enable_if_t<has_get_buffer_view<InputAdapterType>::value>> : std::true_type {
};

FK_YAML_DETAIL_NAMESPACE_END

#endif

#ifndef FK_YAML_DETAIL_NODE_ATTRS_HPP
#define FK_YAML_DETAIL_NODE_ATTRS_HPP

#include <cstdint>
#include <limits>

FK_YAML_DETAIL_NAMESPACE_BEGIN

using node_attr_t = uint32_t;

namespace node_attr_mask {

constexpr node_attr_t value = 0x0000FFFFu;
constexpr node_attr_t style = 0x00FF0000u;
constexpr node_attr_t props = 0xFF000000u;
constexpr node_attr_t anchoring = 0x03000000u;
constexpr node_attr_t anchor_offset = 0xFC000000u;
constexpr node_attr_t all = std::numeric_limits<node_attr_t>::max();

}

namespace node_attr_bits {

constexpr node_attr_t seq_bit = 1u << 0;
constexpr node_attr_t map_bit = 1u << 1;
constexpr node_attr_t null_bit = 1u << 2;
constexpr node_attr_t bool_bit = 1u << 3;
constexpr node_attr_t int_bit = 1u << 4;
constexpr node_attr_t float_bit = 1u << 5;
constexpr node_attr_t string_bit = 1u << 6;

constexpr node_attr_t scalar_bits = null_bit | bool_bit | int_bit | float_bit | string_bit;

constexpr node_attr_t anchor_bit = 0x01000000u;
constexpr node_attr_t alias_bit = 0x02000000u;

constexpr node_attr_t default_bits = null_bit;

inline node_attr_t from_node_type(node_type t) noexcept {
    switch (t) {
    case node_type::SEQUENCE:
        return seq_bit;
    case node_type::MAPPING:
        return map_bit;
    case node_type::NULL_OBJECT:
        return null_bit;
    case node_type::BOOLEAN:
        return bool_bit;
    case node_type::INTEGER:
        return int_bit;
    case node_type::FLOAT:
        return float_bit;
    case node_type::STRING:
        return string_bit;
    default:
        return node_attr_mask::all;
    }
}

inline node_type to_node_type(node_attr_t bits) noexcept {
    switch (bits & node_attr_mask::value) {
    case seq_bit:
        return node_type::SEQUENCE;
    case map_bit:
        return node_type::MAPPING;
    case null_bit:
        return node_type::NULL_OBJECT;
    case bool_bit:
        return node_type::BOOLEAN;
    case int_bit:
        return node_type::INTEGER;
    case float_bit:
        return node_type::FLOAT;
    case string_bit:
        return node_type::STRING;
    default:
        detail::unreachable();
    }
}

inline uint32_t get_anchor_offset(node_attr_t attrs) noexcept {
    return (attrs & node_attr_mask::anchor_offset) >> 26;
}

inline void set_anchor_offset(uint32_t offset, node_attr_t& attrs) noexcept {
    attrs &= ~node_attr_mask::anchor_offset;
    attrs |= (offset & 0x3Fu) << 26;
}

}

FK_YAML_DETAIL_NAMESPACE_END

#endif

#ifndef FK_YAML_DETAIL_NODE_PROPERTY_HPP
#define FK_YAML_DETAIL_NODE_PROPERTY_HPP

#include <string>

FK_YAML_DETAIL_NAMESPACE_BEGIN

struct node_property {
    std::string tag {};
    std::string anchor {};
};

FK_YAML_DETAIL_NAMESPACE_END

#endif

FK_YAML_DETAIL_NAMESPACE_BEGIN

template <typename BasicNodeType>
class basic_deserializer {
    static_assert(is_basic_node<BasicNodeType>::value, "basic_deserializer only accepts basic_node<...>");

    using basic_node_type = BasicNodeType;
    using lexer_type = lexical_analyzer;
    using doc_metainfo_type = document_metainfo<basic_node_type>;
    using tag_resolver_type = tag_resolver<basic_node_type>;
    using scalar_parser_type = scalar_parser<basic_node_type>;
    using sequence_type = typename basic_node_type::sequence_type;
    using mapping_type = typename basic_node_type::mapping_type;

    enum class context_state_t : std::uint8_t {
        BLOCK_MAPPING,
        BLOCK_MAPPING_EXPLICIT_KEY,
        BLOCK_MAPPING_EXPLICIT_VALUE,
        MAPPING_VALUE,
        BLOCK_SEQUENCE,
        BLOCK_SEQUENCE_ENTRY,
        FLOW_SEQUENCE,
        FLOW_SEQUENCE_KEY,
        FLOW_MAPPING,
        FLOW_MAPPING_KEY,
    };

    struct parse_context {
        parse_context() = default;

        parse_context(uint32_t line, uint32_t indent, context_state_t state, basic_node_type* p_node) noexcept
            : line(line),
              indent(indent),
              state(state),
              p_node(p_node) {
        }

        parse_context(const parse_context&) noexcept = default;
        parse_context& operator=(const parse_context&) noexcept = default;
        parse_context(parse_context&&) noexcept = default;
        parse_context& operator=(parse_context&&) noexcept = default;

        ~parse_context() {
            switch (state) {
            case context_state_t::BLOCK_MAPPING_EXPLICIT_KEY:
            case context_state_t::FLOW_SEQUENCE_KEY:
            case context_state_t::FLOW_MAPPING_KEY:
                delete p_node;
                p_node = nullptr;
                break;
            default:
                break;
            }
        }

        uint32_t line {0};
        uint32_t indent {0};
        context_state_t state {context_state_t::BLOCK_MAPPING};
        basic_node_type* p_node {nullptr};
    };

    enum class flow_token_state_t : std::uint8_t {
        NEEDS_VALUE_OR_SUFFIX,
        NEEDS_SEPARATOR_OR_SUFFIX,
    };

public:
    basic_deserializer() = default;

public:
    template <typename InputAdapterType, enable_if_t<is_input_adapter<InputAdapterType>::value, int> = 0>
    basic_node_type deserialize(InputAdapterType&& input_adapter) {
        const str_view input_view = input_adapter.get_buffer_view();
        lexer_type lexer(input_view);

        lexical_token_t type {lexical_token_t::END_OF_BUFFER};
        return deserialize_document(lexer, type);
    }

    template <typename InputAdapterType, enable_if_t<is_input_adapter<InputAdapterType>::value, int> = 0>
    std::vector<basic_node_type> deserialize_docs(InputAdapterType&& input_adapter) {
        const str_view input_view = input_adapter.get_buffer_view();
        lexer_type lexer(input_view);

        std::vector<basic_node_type> nodes {};
        lexical_token_t type {lexical_token_t::END_OF_BUFFER};

        do {
            nodes.emplace_back(deserialize_document(lexer, type));
        } while (type != lexical_token_t::END_OF_BUFFER);

        return nodes;
    }

private:
    basic_node_type deserialize_document(lexer_type& lexer, lexical_token_t& last_type) {
        lexical_token token {};

        basic_node_type root;
        mp_current_node = &root;
        mp_meta = root.mp_meta;

        deserialize_directives(lexer, token);

        uint32_t line = lexer.get_lines_processed();
        uint32_t indent = lexer.get_last_token_begin_pos();
        const bool found_props = deserialize_node_properties(lexer, token, line, indent);

        switch (token.type) {
        case lexical_token_t::SEQUENCE_BLOCK_PREFIX: {
            root = basic_node_type::sequence({basic_node_type()});
            apply_directive_set(root);
            if (found_props) {
                apply_node_properties(root);
            }

            parse_context context(
                lexer.get_lines_processed(), lexer.get_last_token_begin_pos(), context_state_t::BLOCK_SEQUENCE, &root);
            m_context_stack.emplace_back(context);

            mp_current_node = &(root.as_seq().back());
            apply_directive_set(*mp_current_node);
            context.state = context_state_t::BLOCK_SEQUENCE_ENTRY;
            context.p_node = mp_current_node;
            m_context_stack.emplace_back(std::move(context));

            token = lexer.get_next_token();
            line = lexer.get_lines_processed();
            indent = lexer.get_last_token_begin_pos();
            break;
        }
        case lexical_token_t::SEQUENCE_FLOW_BEGIN:
            ++m_flow_context_depth;
            lexer.set_context_state(true);
            root = basic_node_type::sequence();
            apply_directive_set(root);
            apply_node_properties(root);
            m_context_stack.emplace_back(
                lexer.get_lines_processed(), lexer.get_last_token_begin_pos(), context_state_t::FLOW_SEQUENCE, &root);
            token = lexer.get_next_token();
            line = lexer.get_lines_processed();
            indent = lexer.get_last_token_begin_pos();
            break;
        case lexical_token_t::MAPPING_FLOW_BEGIN:
            ++m_flow_context_depth;
            lexer.set_context_state(true);
            root = basic_node_type::mapping();
            apply_directive_set(root);
            apply_node_properties(root);
            m_context_stack.emplace_back(
                lexer.get_lines_processed(), lexer.get_last_token_begin_pos(), context_state_t::FLOW_MAPPING, &root);
            token = lexer.get_next_token();
            line = lexer.get_lines_processed();
            indent = lexer.get_last_token_begin_pos();
            break;
        case lexical_token_t::EXPLICIT_KEY_PREFIX: {
            root = basic_node_type::mapping();
            apply_directive_set(root);
            apply_node_properties(root);
            parse_context context(
                lexer.get_lines_processed(), lexer.get_last_token_begin_pos(), context_state_t::BLOCK_MAPPING, &root);
            m_context_stack.emplace_back(std::move(context));
            line = lexer.get_lines_processed();
            indent = lexer.get_last_token_begin_pos();
            break;
        }
        case lexical_token_t::BLOCK_LITERAL_SCALAR:
        case lexical_token_t::BLOCK_FOLDED_SCALAR:
            break;
        case lexical_token_t::PLAIN_SCALAR:
        case lexical_token_t::SINGLE_QUOTED_SCALAR:
        case lexical_token_t::DOUBLE_QUOTED_SCALAR:
        case lexical_token_t::ALIAS_PREFIX:
            if (found_props && line < lexer.get_lines_processed()) {
                if (m_needs_anchor_impl) {
                    m_root_anchor_name = m_anchor_name;
                    m_needs_anchor_impl = false;
                    m_anchor_name = {};
                }

                if (m_needs_tag_impl) {
                    m_root_tag_name = m_tag_name;
                    m_needs_tag_impl = false;
                    m_tag_name = {};
                }

                line = lexer.get_lines_processed();
                indent = lexer.get_last_token_begin_pos();
            }
            break;
        default:
            break;
        }

        deserialize_node(lexer, token, line, indent, last_type);
        FK_YAML_ASSERT(
            last_type == lexical_token_t::END_OF_BUFFER || last_type == lexical_token_t::END_OF_DIRECTIVES ||
            last_type == lexical_token_t::END_OF_DOCUMENT);

        mp_current_node = nullptr;
        mp_meta.reset();
        m_needs_tag_impl = false;
        m_needs_anchor_impl = false;
        m_flow_context_depth = 0;
        m_flow_token_state = flow_token_state_t::NEEDS_VALUE_OR_SUFFIX;
        m_context_stack.clear();

        return root;
    }

    void deserialize_directives(lexer_type& lexer, lexical_token& last_token) {
        bool lacks_end_of_directives_marker = false;
        lexer.set_document_state(true);

        for (;;) {
            const lexical_token token = lexer.get_next_token();

            switch (token.type) {
            case lexical_token_t::YAML_VER_DIRECTIVE:
                if FK_YAML_UNLIKELY (mp_meta->is_version_specified) {
                    throw parse_error(
                        "YAML version cannot be specified more than once.",
                        lexer.get_lines_processed(),
                        lexer.get_last_token_begin_pos());
                }

                mp_meta->version = convert_yaml_version(lexer.get_yaml_version());
                mp_meta->is_version_specified = true;
                lacks_end_of_directives_marker = true;
                break;
            case lexical_token_t::TAG_DIRECTIVE: {
                const str_view tag_handle_view = lexer.get_tag_handle();
                switch (tag_handle_view.size()) {
                case 1 : {
                    const bool is_already_specified = !mp_meta->primary_handle_prefix.empty();
                    if FK_YAML_UNLIKELY (is_already_specified) {
                        throw parse_error(
                            "Primary handle cannot be specified more than once.",
                            lexer.get_lines_processed(),
                            lexer.get_last_token_begin_pos());
                    }
                    const str_view tag_prefix = lexer.get_tag_prefix();
                    mp_meta->primary_handle_prefix.assign(tag_prefix.begin(), tag_prefix.end());
                    lacks_end_of_directives_marker = true;
                    break;
                }
                case 2 : {
                    const bool is_already_specified = !mp_meta->secondary_handle_prefix.empty();
                    if FK_YAML_UNLIKELY (is_already_specified) {
                        throw parse_error(
                            "Secondary handle cannot be specified more than once.",
                            lexer.get_lines_processed(),
                            lexer.get_last_token_begin_pos());
                    }
                    const str_view tag_prefix = lexer.get_tag_prefix();
                    mp_meta->secondary_handle_prefix.assign(tag_prefix.begin(), tag_prefix.end());
                    lacks_end_of_directives_marker = true;
                    break;
                }
                default : {
                    std::string tag_handle(tag_handle_view.begin(), tag_handle_view.end());
                    const str_view tag_prefix_view = lexer.get_tag_prefix();
                    std::string tag_prefix(tag_prefix_view.begin(), tag_prefix_view.end());
                    const bool is_already_specified =
                        !(mp_meta->named_handle_map.emplace(std::move(tag_handle), std::move(tag_prefix)).second);
                    if FK_YAML_UNLIKELY (is_already_specified) {
                        throw parse_error(
                            "The same named handle cannot be specified more than once.",
                            lexer.get_lines_processed(),
                            lexer.get_last_token_begin_pos());
                    }
                    lacks_end_of_directives_marker = true;
                    break;
                }
                }
                break;
            }
            case lexical_token_t::INVALID_DIRECTIVE:
                break;
            case lexical_token_t::END_OF_DIRECTIVES:
                lacks_end_of_directives_marker = false;
                break;
            default:
                if FK_YAML_UNLIKELY (lacks_end_of_directives_marker) {
                    throw parse_error(
                        "The end of directives marker (---) is missing after directives.",
                        lexer.get_lines_processed(),
                        lexer.get_last_token_begin_pos());
                }
                last_token = token;
                lexer.set_document_state(false);
                return;
            }
        }
    }

    void deserialize_node(
        lexer_type& lexer, const lexical_token& first_token, uint32_t first_line, uint32_t first_indent,
        lexical_token_t& last_type) {
        lexical_token token = first_token;
        uint32_t line = first_line;
        uint32_t indent = first_indent;

        do {
            switch (token.type) {
            case lexical_token_t::EXPLICIT_KEY_PREFIX: {
                const bool needs_to_move_back = indent == 0 || indent < m_context_stack.back().indent;
                if (needs_to_move_back) {
                    pop_to_parent_node(line, indent, [indent](const parse_context& c) {
                        return c.state == context_state_t::BLOCK_MAPPING && indent == c.indent;
                    });
                }

                switch (m_context_stack.back().state) {
                case context_state_t::MAPPING_VALUE:
                case context_state_t::BLOCK_MAPPING_EXPLICIT_KEY:
                case context_state_t::BLOCK_MAPPING_EXPLICIT_VALUE:
                case context_state_t::BLOCK_SEQUENCE_ENTRY:
                    *mp_current_node = basic_node_type::mapping();
                    apply_directive_set(*mp_current_node);
                    m_context_stack.emplace_back(line, indent, context_state_t::BLOCK_MAPPING, mp_current_node);
                    break;
                default:
                    break;
                }

                token = lexer.get_next_token();
                if (token.type == lexical_token_t::SEQUENCE_BLOCK_PREFIX) {
                    auto* p_node = new basic_node_type(node_type::SEQUENCE);
                    m_context_stack.emplace_back(line, indent, context_state_t::BLOCK_MAPPING_EXPLICIT_KEY, p_node);

                    apply_directive_set(*p_node);
                    parse_context context(
                        lexer.get_lines_processed(),
                        lexer.get_last_token_begin_pos(),
                        context_state_t::BLOCK_SEQUENCE,
                        p_node);
                    m_context_stack.emplace_back(context);

                    p_node->as_seq().emplace_back(basic_node_type());
                    mp_current_node = &(p_node->as_seq().back());
                    apply_directive_set(*mp_current_node);
                    context.state = context_state_t::BLOCK_SEQUENCE_ENTRY;
                    context.p_node = mp_current_node;
                    m_context_stack.emplace_back(std::move(context));

                    break;
                }

                m_context_stack.emplace_back(
                    line, indent, context_state_t::BLOCK_MAPPING_EXPLICIT_KEY, new basic_node_type());
                mp_current_node = m_context_stack.back().p_node;
                apply_directive_set(*mp_current_node);
                indent = lexer.get_last_token_begin_pos();
                line = lexer.get_lines_processed();

                continue;
            }
            case lexical_token_t::KEY_SEPARATOR: {
                FK_YAML_ASSERT(!m_context_stack.empty());
                if FK_YAML_UNLIKELY (m_context_stack.back().state == context_state_t::BLOCK_SEQUENCE_ENTRY) {
                    throw parse_error("sequence key should not be empty.", line, indent);
                }

                if (m_flow_context_depth > 0) {
                    break;
                }

                const uint32_t old_indent = indent;
                const uint32_t old_line = line;

                token = lexer.get_next_token();
                line = lexer.get_lines_processed();
                indent = lexer.get_last_token_begin_pos();

                const bool found_props = deserialize_node_properties(lexer, token, line, indent);
                if (found_props && line == lexer.get_lines_processed()) {
                    continue;
                }

                line = lexer.get_lines_processed();
                indent = lexer.get_last_token_begin_pos();

                const bool is_implicit_same_line =
                    (line == old_line) && (m_context_stack.empty() || old_indent > m_context_stack.back().indent);
                if (is_implicit_same_line) {
                    continue;
                }

                if (line > old_line) {
                    if (m_needs_tag_impl) {
                        const tag_t tag_type = tag_resolver_type::resolve_tag(m_tag_name, mp_meta);
                        if (tag_type == tag_t::MAPPING || tag_type == tag_t::CUSTOM_TAG) {
                            *mp_current_node = basic_node_type::mapping();
                            apply_directive_set(*mp_current_node);
                            apply_node_properties(*mp_current_node);
                            m_context_stack.emplace_back(line, indent, context_state_t::BLOCK_MAPPING, mp_current_node);
                            continue;
                        }
                    }

                    if (token.type == lexical_token_t::SEQUENCE_BLOCK_PREFIX) {
                        *mp_current_node = basic_node_type::sequence({basic_node_type()});
                        apply_directive_set(*mp_current_node);
                        apply_node_properties(*mp_current_node);
                        auto& cur_context = m_context_stack.back();
                        cur_context.line = line;
                        cur_context.indent = indent;
                        cur_context.state = context_state_t::BLOCK_SEQUENCE;

                        mp_current_node = &(mp_current_node->as_seq().back());
                        apply_directive_set(*mp_current_node);
                        parse_context entry_context = cur_context;
                        entry_context.state = context_state_t::BLOCK_SEQUENCE_ENTRY;
                        entry_context.p_node = mp_current_node;
                        m_context_stack.emplace_back(std::move(entry_context));

                        token = lexer.get_next_token();
                        line = lexer.get_lines_processed();
                        indent = lexer.get_last_token_begin_pos();

                        const bool has_props = deserialize_node_properties(lexer, token, line, indent);
                        if (has_props) {
                            const uint32_t line_after_props = lexer.get_lines_processed();
                            if (line == line_after_props) {
                                continue;
                            }

                            line = line_after_props;
                            indent = lexer.get_last_token_begin_pos();
                            *mp_current_node = basic_node_type::mapping();
                            m_context_stack.emplace_back(
                                line_after_props, indent, context_state_t::BLOCK_MAPPING, mp_current_node);
                            apply_directive_set(*mp_current_node);
                            apply_node_properties(*mp_current_node);
                        }

                        continue;
                    }

                    if (indent <= m_context_stack.back().indent) {
                        FK_YAML_ASSERT(m_context_stack.back().state == context_state_t::MAPPING_VALUE);

                        pop_to_parent_node(line, indent, [indent](const parse_context& c) {
                            return (c.state == context_state_t::BLOCK_MAPPING) && (indent == c.indent);
                        });
                    }

                    continue;
                }

                FK_YAML_ASSERT(m_context_stack.back().state == context_state_t::BLOCK_MAPPING_EXPLICIT_KEY);

                basic_node_type key_node = std::move(*m_context_stack.back().p_node);
                m_context_stack.pop_back();
                m_context_stack.back().p_node->as_map().emplace(key_node, basic_node_type());
                mp_current_node = &(m_context_stack.back().p_node->operator[](std::move(key_node)));
                m_context_stack.emplace_back(
                    old_line, old_indent, context_state_t::BLOCK_MAPPING_EXPLICIT_VALUE, mp_current_node);

                if (token.type == lexical_token_t::SEQUENCE_BLOCK_PREFIX) {
                    *mp_current_node = basic_node_type::sequence({basic_node_type()});
                    apply_directive_set(*mp_current_node);
                    apply_node_properties(*mp_current_node);
                    m_context_stack.emplace_back(line, indent, context_state_t::BLOCK_SEQUENCE, mp_current_node);

                    mp_current_node = &(mp_current_node->as_seq().back());
                    parse_context entry_context = m_context_stack.back();
                    entry_context.state = context_state_t::BLOCK_SEQUENCE_ENTRY;
                    entry_context.p_node = mp_current_node;
                    m_context_stack.emplace_back(std::move(entry_context));
                    break;
                }

                continue;
            }
            case lexical_token_t::ANCHOR_PREFIX:
            case lexical_token_t::TAG_PREFIX:
                deserialize_node_properties(lexer, token, line, indent);
                continue;
            case lexical_token_t::SEQUENCE_BLOCK_PREFIX: {
                FK_YAML_ASSERT(!m_context_stack.empty());
                const uint32_t parent_indent = m_context_stack.back().indent;
                if (indent == parent_indent) {
                    pop_to_parent_node(line, indent, [](const parse_context& c) {
                        return c.state == context_state_t::BLOCK_SEQUENCE;
                    });
                }
                else if (indent < parent_indent) {
                    pop_to_parent_node(line, indent, [indent](const parse_context& c) {
                        return c.state == context_state_t::BLOCK_SEQUENCE && indent == c.indent;
                    });
                }
                else {
                    if FK_YAML_UNLIKELY (m_context_stack.back().state == context_state_t::BLOCK_SEQUENCE) {
                        throw parse_error("bad indentation of a mapping entry.", line, indent);
                    }

                    *mp_current_node = basic_node_type::sequence();
                    m_context_stack.emplace_back(line, indent, context_state_t::BLOCK_SEQUENCE, mp_current_node);
                    apply_directive_set(*mp_current_node);
                    apply_node_properties(*mp_current_node);
                }

                auto& seq = mp_current_node->as_seq();
                seq.emplace_back(basic_node_type());
                mp_current_node = &(seq.back());
                apply_directive_set(*mp_current_node);
                m_context_stack.emplace_back(line, indent, context_state_t::BLOCK_SEQUENCE_ENTRY, mp_current_node);
                break;
            }
            case lexical_token_t::SEQUENCE_FLOW_BEGIN:
                if (m_flow_context_depth == 0) {
                    lexer.set_context_state(true);

                    if (indent <= m_context_stack.back().indent) {
                        pop_to_parent_node(line, indent, [indent](const parse_context& c) {
                            switch (c.state) {
                            case context_state_t::BLOCK_MAPPING:
                            case context_state_t::MAPPING_VALUE:
                                return indent == c.indent;
                            default:
                                return false;
                            }
                        });
                    }
                }
                else if FK_YAML_UNLIKELY (m_flow_token_state == flow_token_state_t::NEEDS_SEPARATOR_OR_SUFFIX) {
                    throw parse_error("Flow sequence beginning is found without separated with a comma.", line, indent);
                }

                ++m_flow_context_depth;

                switch (m_context_stack.back().state) {
                case context_state_t::BLOCK_SEQUENCE:
                case context_state_t::FLOW_SEQUENCE:
                    mp_current_node->as_seq().emplace_back(basic_node_type::sequence());
                    mp_current_node = &(mp_current_node->as_seq().back());
                    m_context_stack.emplace_back(line, indent, context_state_t::FLOW_SEQUENCE, mp_current_node);
                    break;
                case context_state_t::BLOCK_MAPPING:
                case context_state_t::FLOW_MAPPING:
                    m_context_stack.emplace_back(
                        line, indent, context_state_t::FLOW_SEQUENCE_KEY, new basic_node_type(node_type::SEQUENCE));
                    mp_current_node = m_context_stack.back().p_node;
                    break;
                default: {
                    *mp_current_node = basic_node_type::sequence();
                    parse_context& last_context = m_context_stack.back();
                    last_context.line = line;
                    last_context.indent = indent;
                    last_context.state = context_state_t::FLOW_SEQUENCE;
                    break;
                }
                }

                apply_directive_set(*mp_current_node);
                apply_node_properties(*mp_current_node);

                m_flow_token_state = flow_token_state_t::NEEDS_VALUE_OR_SUFFIX;
                break;
            case lexical_token_t::SEQUENCE_FLOW_END: {
                if FK_YAML_UNLIKELY (m_flow_context_depth == 0) {
                    throw parse_error("Flow sequence ending is found outside the flow context.", line, indent);
                }

                if (--m_flow_context_depth == 0) {
                    lexer.set_context_state(false);
                }

                auto itr = std::find_if(
                    m_context_stack.rbegin(),
                    m_context_stack.rend(),
                    [](const parse_context& c) {
                        switch (c.state) {
                        case context_state_t::FLOW_SEQUENCE_KEY:
                        case context_state_t::FLOW_SEQUENCE:
                            return true;
                        default:
                            return false;
                        }
                    });

                const bool is_valid = itr != m_context_stack.rend();
                if FK_YAML_UNLIKELY (!is_valid) {
                    throw parse_error("No corresponding flow sequence beginning is found.", line, indent);
                }

                parse_context& last_context = m_context_stack.back();
                mp_current_node = last_context.p_node;
                last_context.p_node = nullptr;
                indent = last_context.indent;
                const context_state_t state = last_context.state;
                m_context_stack.pop_back();

                if (!m_context_stack.empty() && state == context_state_t::FLOW_SEQUENCE_KEY) {
                    basic_node_type key_node = std::move(*mp_current_node);
                    delete mp_current_node;
                    mp_current_node = m_context_stack.back().p_node;
                    m_flow_token_state = flow_token_state_t::NEEDS_VALUE_OR_SUFFIX;

                    add_new_key(std::move(key_node), line, indent);
                    break;
                }

                token = lexer.get_next_token();
                if (token.type == lexical_token_t::KEY_SEPARATOR) {
                    basic_node_type key_node = basic_node_type::mapping();
                    apply_directive_set(key_node);
                    mp_current_node->swap(key_node);

                    m_context_stack.emplace_back(line, indent, context_state_t::BLOCK_MAPPING, mp_current_node);
                    m_flow_token_state = flow_token_state_t::NEEDS_VALUE_OR_SUFFIX;

                    add_new_key(std::move(key_node), line, indent);
                }
                else {
                    if (!m_context_stack.empty()) {
                        mp_current_node = m_context_stack.back().p_node;
                    }
                    if (m_flow_context_depth > 0) {
                        m_flow_token_state = flow_token_state_t::NEEDS_SEPARATOR_OR_SUFFIX;
                    }
                }

                indent = lexer.get_last_token_begin_pos();
                line = lexer.get_lines_processed();
                continue;
            }
            case lexical_token_t::MAPPING_FLOW_BEGIN:
                if (m_flow_context_depth == 0) {
                    lexer.set_context_state(true);

                    if (indent <= m_context_stack.back().indent) {
                        pop_to_parent_node(line, indent, [indent](const parse_context& c) {
                            switch (c.state) {
                            case context_state_t::BLOCK_MAPPING:
                            case context_state_t::MAPPING_VALUE:
                                return indent == c.indent;
                            default:
                                return false;
                            }
                        });
                    }
                }
                else if FK_YAML_UNLIKELY (m_flow_token_state == flow_token_state_t::NEEDS_SEPARATOR_OR_SUFFIX) {
                    throw parse_error("Flow mapping beginning is found without separated with a comma.", line, indent);
                }

                ++m_flow_context_depth;

                switch (m_context_stack.back().state) {
                case context_state_t::BLOCK_SEQUENCE:
                case context_state_t::FLOW_SEQUENCE:
                    mp_current_node->as_seq().emplace_back(basic_node_type::mapping());
                    mp_current_node = &(mp_current_node->as_seq().back());
                    m_context_stack.emplace_back(line, indent, context_state_t::FLOW_MAPPING, mp_current_node);
                    break;
                case context_state_t::BLOCK_MAPPING:
                case context_state_t::FLOW_MAPPING:
                    m_context_stack.emplace_back(
                        line, indent, context_state_t::FLOW_MAPPING_KEY, new basic_node_type(node_type::MAPPING));
                    mp_current_node = m_context_stack.back().p_node;
                    break;
                default: {
                    *mp_current_node = basic_node_type::mapping();
                    parse_context& last_context = m_context_stack.back();
                    last_context.line = line;
                    last_context.indent = indent;
                    last_context.state = context_state_t::FLOW_MAPPING;
                    break;
                }
                }

                apply_directive_set(*mp_current_node);
                apply_node_properties(*mp_current_node);

                line = lexer.get_lines_processed();
                indent = lexer.get_last_token_begin_pos();

                m_flow_token_state = flow_token_state_t::NEEDS_VALUE_OR_SUFFIX;
                break;
            case lexical_token_t::MAPPING_FLOW_END: {
                if FK_YAML_UNLIKELY (m_flow_context_depth == 0) {
                    throw parse_error("Flow mapping ending is found outside the flow context.", line, indent);
                }

                if (--m_flow_context_depth == 0) {
                    lexer.set_context_state(false);
                }

                auto itr = std::find_if(
                    m_context_stack.rbegin(),
                    m_context_stack.rend(),
                    [](const parse_context& c) {
                        switch (c.state) {
                        case context_state_t::FLOW_MAPPING_KEY:
                        case context_state_t::FLOW_MAPPING:
                            return true;
                        default:
                            return false;
                        }
                    });

                const bool is_valid = itr != m_context_stack.rend();
                if FK_YAML_UNLIKELY (!is_valid) {
                    throw parse_error("No corresponding flow mapping beginning is found.", line, indent);
                }

                parse_context& last_context = m_context_stack.back();
                mp_current_node = last_context.p_node;
                last_context.p_node = nullptr;
                indent = last_context.indent;
                const context_state_t state = last_context.state;
                m_context_stack.pop_back();

                if (!m_context_stack.empty() && state == context_state_t::FLOW_MAPPING_KEY) {
                    basic_node_type key_node = std::move(*mp_current_node);
                    delete mp_current_node;
                    mp_current_node = m_context_stack.back().p_node;
                    m_flow_token_state = flow_token_state_t::NEEDS_VALUE_OR_SUFFIX;

                    add_new_key(std::move(key_node), line, indent);
                    break;
                }

                token = lexer.get_next_token();
                if (token.type == lexical_token_t::KEY_SEPARATOR) {
                    basic_node_type key_node = basic_node_type::mapping();
                    apply_directive_set(key_node);
                    mp_current_node->swap(key_node);

                    m_context_stack.emplace_back(line, indent, context_state_t::BLOCK_MAPPING, mp_current_node);
                    m_flow_token_state = flow_token_state_t::NEEDS_VALUE_OR_SUFFIX;

                    add_new_key(std::move(key_node), line, indent);
                }
                else {
                    if (!m_context_stack.empty()) {
                        mp_current_node = m_context_stack.back().p_node;
                    }
                    if (m_flow_context_depth > 0) {
                        m_flow_token_state = flow_token_state_t::NEEDS_SEPARATOR_OR_SUFFIX;
                    }
                }

                indent = lexer.get_last_token_begin_pos();
                line = lexer.get_lines_processed();
                continue;
            }
            case lexical_token_t::VALUE_SEPARATOR:
                FK_YAML_ASSERT(m_flow_context_depth > 0);
                if FK_YAML_UNLIKELY (m_flow_token_state != flow_token_state_t::NEEDS_SEPARATOR_OR_SUFFIX) {
                    throw parse_error("invalid value separator is found.", line, indent);
                }
                m_flow_token_state = flow_token_state_t::NEEDS_VALUE_OR_SUFFIX;
                break;
            case lexical_token_t::ALIAS_PREFIX: {
                if FK_YAML_UNLIKELY (m_needs_tag_impl) {
                    throw parse_error("Tag cannot be specified to an alias node", line, indent);
                }
                if FK_YAML_UNLIKELY (m_needs_anchor_impl) {
                    throw parse_error("Anchor cannot be specified to an alias node.", line, indent);
                }

                std::string token_str = std::string(token.str.begin(), token.str.end());

                const auto anchor_counts = static_cast<uint32_t>(mp_meta->anchor_table.count(token_str));
                if FK_YAML_UNLIKELY (anchor_counts == 0) {
                    throw parse_error("The given anchor name must appear prior to the alias node.", line, indent);
                }

                basic_node_type node {};
                node.m_attrs |= detail::node_attr_bits::alias_bit;
                node.m_prop.anchor = std::move(token_str);
                detail::node_attr_bits::set_anchor_offset(anchor_counts - 1, node.m_attrs);

                apply_directive_set(node);
                apply_node_properties(node);

                deserialize_scalar(lexer, std::move(node), indent, line, token);
                continue;
            }
            case lexical_token_t::PLAIN_SCALAR:
            case lexical_token_t::SINGLE_QUOTED_SCALAR:
            case lexical_token_t::DOUBLE_QUOTED_SCALAR: {
                tag_t tag_type {tag_t::NONE};
                if (m_needs_tag_impl) {
                    tag_type = tag_resolver_type::resolve_tag(m_tag_name, mp_meta);
                }

                basic_node_type node = scalar_parser_type(line, indent).parse_flow(token.type, tag_type, token.str);
                apply_directive_set(node);
                apply_node_properties(node);

                deserialize_scalar(lexer, std::move(node), indent, line, token);
                continue;
            }
            case lexical_token_t::BLOCK_LITERAL_SCALAR:
            case lexical_token_t::BLOCK_FOLDED_SCALAR: {
                tag_t tag_type {tag_t::NONE};
                if (m_needs_tag_impl) {
                    tag_type = tag_resolver_type::resolve_tag(m_tag_name, mp_meta);
                }

                basic_node_type node =
                    scalar_parser_type(line, indent)
                        .parse_block(token.type, tag_type, token.str, lexer.get_block_scalar_header());
                apply_directive_set(node);
                apply_node_properties(node);

                deserialize_scalar(lexer, std::move(node), indent, line, token);
                continue;
            }
            case lexical_token_t::END_OF_BUFFER:
                last_type = token.type;
                return;
            case lexical_token_t::END_OF_DIRECTIVES:
            case lexical_token_t::END_OF_DOCUMENT:
                if FK_YAML_UNLIKELY (m_flow_context_depth > 0) {
                    throw parse_error("An invalid document marker found in a flow collection", line, indent);
                }
                last_type = token.type;
                return;
            case lexical_token_t::YAML_VER_DIRECTIVE:
            case lexical_token_t::TAG_DIRECTIVE:
            case lexical_token_t::INVALID_DIRECTIVE:
                detail::unreachable();
            }

            token = lexer.get_next_token();
            indent = lexer.get_last_token_begin_pos();
            line = lexer.get_lines_processed();
        } while (token.type != lexical_token_t::END_OF_BUFFER);

        last_type = token.type;
    }

    bool deserialize_node_properties(lexer_type& lexer, lexical_token& last_token, uint32_t& line, uint32_t& indent) {
        m_needs_anchor_impl = m_needs_tag_impl = false;

        lexical_token token = last_token;
        bool ends_loop {false};
        do {
            if (line < lexer.get_lines_processed()) {
                break;
            }

            switch (token.type) {
            case lexical_token_t::ANCHOR_PREFIX:
                if FK_YAML_UNLIKELY (m_needs_anchor_impl) {
                    throw parse_error(
                        "anchor name cannot be specified more than once to the same node.",
                        lexer.get_lines_processed(),
                        lexer.get_last_token_begin_pos());
                }

                m_anchor_name = token.str;
                m_needs_anchor_impl = true;

                if (!m_needs_tag_impl) {
                    line = lexer.get_lines_processed();
                    indent = lexer.get_last_token_begin_pos();
                }

                token = lexer.get_next_token();
                break;
            case lexical_token_t::TAG_PREFIX: {
                if FK_YAML_UNLIKELY (m_needs_tag_impl) {
                    throw parse_error(
                        "tag name cannot be specified more than once to the same node.",
                        lexer.get_lines_processed(),
                        lexer.get_last_token_begin_pos());
                }

                m_tag_name = token.str;
                m_needs_tag_impl = true;

                if (!m_needs_anchor_impl) {
                    line = lexer.get_lines_processed();
                    indent = lexer.get_last_token_begin_pos();
                }

                token = lexer.get_next_token();
                break;
            }
            default:
                ends_loop = true;
                break;
            }
        } while (!ends_loop);

        last_token = token;
        const bool prop_specified = m_needs_anchor_impl || m_needs_tag_impl;
        if (!prop_specified) {
            line = lexer.get_lines_processed();
            indent = lexer.get_last_token_begin_pos();
        }

        return prop_specified;
    }

    void add_new_key(basic_node_type&& key, const uint32_t line, const uint32_t indent) {
        if (m_flow_context_depth == 0) {
            if FK_YAML_UNLIKELY (m_context_stack.back().indent < indent) {
                throw parse_error("bad indentation of a mapping entry.", line, indent);
            }

            pop_to_parent_node(line, indent, [indent](const parse_context& c) {
                return (c.state == context_state_t::BLOCK_MAPPING) && (indent == c.indent);
            });
        }
        else {
            if FK_YAML_UNLIKELY (m_flow_token_state != flow_token_state_t::NEEDS_VALUE_OR_SUFFIX) {
                throw parse_error("Flow mapping entry is found without separated with a comma.", line, indent);
            }

            if (mp_current_node->is_sequence()) {
                mp_current_node->as_seq().emplace_back(basic_node_type::mapping());
                mp_current_node = &(mp_current_node->operator[](mp_current_node->size() - 1));
                m_context_stack.emplace_back(line, indent, context_state_t::BLOCK_MAPPING, mp_current_node);
            }
        }

        auto itr = mp_current_node->as_map().emplace(std::move(key), basic_node_type());
        if FK_YAML_UNLIKELY (!itr.second) {
            throw parse_error("Detected duplication in mapping keys.", line, indent);
        }

        mp_current_node = &(itr.first->second);
        const parse_context& key_context = m_context_stack.back();
        m_context_stack.emplace_back(
            key_context.line, key_context.indent, context_state_t::MAPPING_VALUE, mp_current_node);
    }

    void assign_node_value(basic_node_type&& node_value, const uint32_t line, const uint32_t indent) {
        if (mp_current_node->is_sequence()) {
            FK_YAML_ASSERT(m_flow_context_depth > 0);

            if FK_YAML_UNLIKELY (m_flow_token_state != flow_token_state_t::NEEDS_VALUE_OR_SUFFIX) {
                throw parse_error("flow sequence entry is found without separated with a comma.", line, indent);
            }

            mp_current_node->as_seq().emplace_back(std::move(node_value));
            m_flow_token_state = flow_token_state_t::NEEDS_SEPARATOR_OR_SUFFIX;
            return;
        }

        *mp_current_node = std::move(node_value);
        if FK_YAML_UNLIKELY (m_context_stack.empty()) {
            return;
        }

        if FK_YAML_LIKELY (m_context_stack.back().state != context_state_t::BLOCK_MAPPING_EXPLICIT_KEY) {
            m_context_stack.pop_back();
            mp_current_node = m_context_stack.back().p_node;

            if (m_flow_context_depth > 0) {
                m_flow_token_state = flow_token_state_t::NEEDS_SEPARATOR_OR_SUFFIX;
            }
        }
    }

    void deserialize_scalar(
        lexer_type& lexer, basic_node_type&& node, uint32_t& indent, uint32_t& line, lexical_token& token) {
        token = lexer.get_next_token();
        if (mp_current_node->is_mapping()) {
            const bool is_key_sep_followed =
                (token.type == lexical_token_t::KEY_SEPARATOR) && (line == lexer.get_lines_processed());
            if FK_YAML_UNLIKELY (!is_key_sep_followed) {
                throw parse_error(
                    "The \":\" mapping value indicator must be followed after a mapping key.",
                    lexer.get_lines_processed(),
                    lexer.get_last_token_begin_pos());
            }
            add_new_key(std::move(node), line, indent);
        }
        else if (token.type == lexical_token_t::KEY_SEPARATOR) {
            if FK_YAML_UNLIKELY (line != lexer.get_lines_processed()) {
                assign_node_value(std::move(node), line, indent);
                indent = lexer.get_last_token_begin_pos();
                line = lexer.get_lines_processed();

                if (m_context_stack.back().state != context_state_t::BLOCK_MAPPING_EXPLICIT_KEY) {
                    pop_to_parent_node(line, indent, [indent](const parse_context& c) {
                        return c.state == context_state_t::BLOCK_MAPPING_EXPLICIT_KEY && indent == c.indent;
                    });
                }
                return;
            }

            if (mp_current_node->is_scalar()) {
                if FK_YAML_LIKELY (!m_context_stack.empty()) {
                    parse_context& cur_context = m_context_stack.back();
                    switch (cur_context.state) {
                    case context_state_t::BLOCK_MAPPING_EXPLICIT_KEY:
                    case context_state_t::BLOCK_MAPPING_EXPLICIT_VALUE:
                        m_context_stack.emplace_back(line, indent, context_state_t::BLOCK_MAPPING, mp_current_node);
                        break;
                    case context_state_t::BLOCK_SEQUENCE_ENTRY:
                        if FK_YAML_UNLIKELY (cur_context.indent >= indent) {
                            pop_to_parent_node(line, indent, [indent](const parse_context& c) {
                                return c.state == context_state_t::BLOCK_MAPPING && indent == c.indent;
                            });
                            add_new_key(std::move(node), line, indent);
                            indent = lexer.get_last_token_begin_pos();
                            line = lexer.get_lines_processed();
                            return;
                        }

                        m_context_stack.emplace_back(line, indent, context_state_t::BLOCK_MAPPING, mp_current_node);
                        break;
                    default:
                        if FK_YAML_UNLIKELY (cur_context.line == line) {
                            throw parse_error("Multiple mapping keys are specified on the same line.", line, indent);
                        }
                        cur_context.line = line;
                        cur_context.indent = indent;
                        cur_context.state = context_state_t::BLOCK_MAPPING;
                        break;
                    }

                    *mp_current_node = basic_node_type::mapping();
                    apply_directive_set(*mp_current_node);
                }
                else {
                    m_context_stack.emplace_back(line, indent, context_state_t::BLOCK_MAPPING, mp_current_node);
                    *mp_current_node = basic_node_type::mapping();
                    apply_directive_set(*mp_current_node);

                    if (!m_root_anchor_name.empty()) {
                        mp_current_node->add_anchor_name(
                            std::string(m_root_anchor_name.begin(), m_root_anchor_name.end()));
                        m_root_anchor_name = {};
                    }
                    if (!m_root_tag_name.empty()) {
                        mp_current_node->add_tag_name(std::string(m_root_tag_name.begin(), m_root_tag_name.end()));
                        m_root_tag_name = {};
                    }
                }
            }
            add_new_key(std::move(node), line, indent);
        }
        else {
            assign_node_value(std::move(node), line, indent);
        }

        indent = lexer.get_last_token_begin_pos();
        line = lexer.get_lines_processed();
    }

    template <typename Pred>
    void pop_to_parent_node(uint32_t line, uint32_t indent, Pred&& pred) {
        FK_YAML_ASSERT(!m_context_stack.empty());

        auto itr = std::find_if(m_context_stack.rbegin(), m_context_stack.rend(), std::forward<Pred>(pred));
        const bool is_indent_valid = (itr != m_context_stack.rend());
        if FK_YAML_UNLIKELY (!is_indent_valid) {
            throw parse_error("Detected invalid indentation.", line, indent);
        }

        const auto pop_num = static_cast<uint32_t>(std::distance(m_context_stack.rbegin(), itr));

        for (uint32_t i = 0; i < pop_num; i++) {
            m_context_stack.pop_back();
        }
        mp_current_node = m_context_stack.back().p_node;
    }

    void apply_directive_set(basic_node_type& node) noexcept {
        node.mp_meta = mp_meta;
    }

    void apply_node_properties(basic_node_type& node) {
        if (m_needs_anchor_impl) {
            node.add_anchor_name(std::string(m_anchor_name.begin(), m_anchor_name.end()));
            m_needs_anchor_impl = false;
            m_anchor_name = {};
        }

        if (m_needs_tag_impl) {
            node.add_tag_name(std::string(m_tag_name.begin(), m_tag_name.end()));
            m_needs_tag_impl = false;
            m_tag_name = {};
        }
    }

    yaml_version_type convert_yaml_version(str_view version_str) noexcept {
        return (version_str.compare("1.1") == 0) ? yaml_version_type::VERSION_1_1 : yaml_version_type::VERSION_1_2;
    }

private:
    struct context_stack_type : std::deque<parse_context> {
        using base_type = std::deque<parse_context>;

        parse_context& back() {
            throw_if_empty();
            return base_type::back();
        }

        const parse_context& back() const {
            throw_if_empty();
            return base_type::back();
        }

        void pop_back() {
            throw_if_empty();
            base_type::pop_back();
        }

        void throw_if_empty() const {
            if FK_YAML_UNLIKELY (base_type::empty()) {
                throw parse_error("Invalid document structure.", 0, 0);
            }
        }
    };

    basic_node_type* mp_current_node {nullptr};
    context_stack_type m_context_stack {};
    uint32_t m_flow_context_depth {0};
    std::shared_ptr<doc_metainfo_type> mp_meta {};
    bool m_needs_anchor_impl {false};
    bool m_needs_tag_impl {false};
    flow_token_state_t m_flow_token_state {flow_token_state_t::NEEDS_VALUE_OR_SUFFIX};
    str_view m_anchor_name;
    str_view m_tag_name;
    str_view m_root_anchor_name;
    str_view m_root_tag_name;
};

FK_YAML_DETAIL_NAMESPACE_END

#endif

#ifndef FK_YAML_DETAIL_INPUT_INPUT_ADAPTER_HPP
#define FK_YAML_DETAIL_INPUT_INPUT_ADAPTER_HPP

#include <array>
#include <cstdio>
#include <cstring>
#include <deque>
#include <istream>
#include <iterator>
#include <string>

#ifndef FK_YAML_DETAIL_ENCODINGS_UTF_ENCODE_DETECTOR_HPP
#define FK_YAML_DETAIL_ENCODINGS_UTF_ENCODE_DETECTOR_HPP

#include <cstdint>
#include <istream>

#ifndef FK_YAML_DETAIL_ENCODINGS_UTF_ENCODE_T_HPP
#define FK_YAML_DETAIL_ENCODINGS_UTF_ENCODE_T_HPP

#include <cstdint>

FK_YAML_DETAIL_NAMESPACE_BEGIN

enum class utf_encode_t : std::uint8_t {
    UTF_8,
    UTF_16BE,
    UTF_16LE,
    UTF_32BE,
    UTF_32LE,
};

FK_YAML_DETAIL_NAMESPACE_END

#endif

FK_YAML_DETAIL_NAMESPACE_BEGIN

inline utf_encode_t detect_encoding_type(const std::array<uint8_t, 4>& bytes, bool& has_bom) noexcept {
    has_bom = false;

    const uint8_t byte0 = bytes[0];
    const uint8_t byte1 = bytes[1];
    const uint8_t byte2 = bytes[2];
    const uint8_t byte3 = bytes[3];

    if (byte0 == static_cast<uint8_t>(0xEFu) && byte1 == static_cast<uint8_t>(0xBBu) &&
        byte2 == static_cast<uint8_t>(0xBFu)) {
        has_bom = true;
        return utf_encode_t::UTF_8;
    }

    if (byte0 == 0 && byte1 == 0 && byte2 == static_cast<uint8_t>(0xFEu) && byte3 == static_cast<uint8_t>(0xFFu)) {
        has_bom = true;
        return utf_encode_t::UTF_32BE;
    }

    if (byte0 == static_cast<uint8_t>(0xFFu) && byte1 == static_cast<uint8_t>(0xFEu) && byte2 == 0 && byte3 == 0) {
        has_bom = true;
        return utf_encode_t::UTF_32LE;
    }

    if (byte0 == static_cast<uint8_t>(0xFEu) && byte1 == static_cast<uint8_t>(0xFFu)) {
        has_bom = true;
        return utf_encode_t::UTF_16BE;
    }

    if (byte0 == static_cast<uint8_t>(0xFFu) && byte1 == static_cast<uint8_t>(0xFEu)) {
        has_bom = true;
        return utf_encode_t::UTF_16LE;
    }

    if (byte0 == 0 && byte1 == 0 && byte2 == 0 && 0 < byte3 && byte3 < static_cast<uint8_t>(0x80u)) {
        return utf_encode_t::UTF_32BE;
    }

    if (0 < byte0 && byte0 < static_cast<uint8_t>(0x80u) && byte1 == 0 && byte2 == 0 && byte3 == 0) {
        return utf_encode_t::UTF_32LE;
    }

    if (byte0 == 0 && 0 < byte1 && byte1 < static_cast<uint8_t>(0x80u)) {
        return utf_encode_t::UTF_16BE;
    }

    if (0 < byte0 && byte0 < static_cast<uint8_t>(0x80u) && byte1 == 0) {
        return utf_encode_t::UTF_16LE;
    }

    return utf_encode_t::UTF_8;
}

template <typename ItrType, typename = void>
struct utf_encode_detector {};

template <typename ItrType>
struct utf_encode_detector<ItrType, enable_if_t<is_iterator_of<ItrType, char>::value>> {
    static utf_encode_t detect(ItrType& begin, const ItrType& end) noexcept {
        if FK_YAML_UNLIKELY (begin == end) {
            return utf_encode_t::UTF_8;
        }

        std::array<uint8_t, 4> bytes {{}};
        bytes.fill(0xFFu);
        auto current = begin;
        for (int i = 0; i < 4 && current != end; i++, ++current) {
            bytes[i] = static_cast<uint8_t>(*current);
        }

        bool has_bom = false;
        const utf_encode_t encode_type = detect_encoding_type(bytes, has_bom);

        if (has_bom) {
            switch (encode_type) {
            case utf_encode_t::UTF_8:
                std::advance(begin, 3);
                break;
            case utf_encode_t::UTF_16BE:
            case utf_encode_t::UTF_16LE:
                std::advance(begin, 2);
                break;
            case utf_encode_t::UTF_32BE:
            case utf_encode_t::UTF_32LE:
                std::advance(begin, 4);
                break;
            }
        }

        return encode_type;
    }
};

#if FK_YAML_HAS_CHAR8_T

template <typename ItrType>
struct utf_encode_detector<ItrType, enable_if_t<is_iterator_of<ItrType, char8_t>::value>> {
    static utf_encode_t detect(ItrType& begin, const ItrType& end) {
        if FK_YAML_UNLIKELY (begin == end) {
            return utf_encode_t::UTF_8;
        }

        std::array<uint8_t, 4> bytes {};
        bytes.fill(0xFFu);
        auto current = begin;
        for (int i = 0; i < 4 && current != end; i++, ++current) {
            bytes[i] = uint8_t(*current);
        }

        bool has_bom = false;
        const utf_encode_t encode_type = detect_encoding_type(bytes, has_bom);

        if FK_YAML_UNLIKELY (encode_type != utf_encode_t::UTF_8) {
            throw exception("char8_t characters must be encoded in the UTF-8 format.");
        }

        if (has_bom) {
            std::advance(begin, 3);
        }

        return encode_type;
    }
};

#endif

template <typename ItrType>
struct utf_encode_detector<ItrType, enable_if_t<is_iterator_of<ItrType, char16_t>::value>> {
    static utf_encode_t detect(ItrType& begin, const ItrType& end) {
        if FK_YAML_UNLIKELY (begin == end) {
            return utf_encode_t::UTF_16BE;
        }

        std::array<uint8_t, 4> bytes {{}};
        bytes.fill(0xFFu);
        auto current = begin;
        for (int i = 0; i < 2 && current != end; i++, ++current) {
            const char16_t elem = *current;
            const int idx_base = i * 2;
            bytes[idx_base] = static_cast<uint8_t>(elem >> 8);
            bytes[idx_base + 1] = static_cast<uint8_t>(elem);
        }

        bool has_bom = false;
        const utf_encode_t encode_type = detect_encoding_type(bytes, has_bom);

        if FK_YAML_UNLIKELY (encode_type != utf_encode_t::UTF_16BE && encode_type != utf_encode_t::UTF_16LE) {
            throw exception("char16_t characters must be encoded in the UTF-16 format.");
        }

        if (has_bom) {
            std::advance(begin, 1);
        }

        return encode_type;
    }
};

template <typename ItrType>
struct utf_encode_detector<ItrType, enable_if_t<is_iterator_of<ItrType, char32_t>::value>> {
    static utf_encode_t detect(ItrType& begin, const ItrType& end) {
        if FK_YAML_UNLIKELY (begin == end) {
            return utf_encode_t::UTF_32BE;
        }

        std::array<uint8_t, 4> bytes {{}};
        const char32_t elem = *begin;
        bytes[0] = static_cast<uint8_t>(elem >> 24);
        bytes[1] = static_cast<uint8_t>(elem >> 16);
        bytes[2] = static_cast<uint8_t>(elem >> 8);
        bytes[3] = static_cast<uint8_t>(elem);

        bool has_bom = false;
        const utf_encode_t encode_type = detect_encoding_type(bytes, has_bom);

        if FK_YAML_UNLIKELY (encode_type != utf_encode_t::UTF_32BE && encode_type != utf_encode_t::UTF_32LE) {
            throw exception("char32_t characters must be encoded in the UTF-32 format.");
        }

        if (has_bom) {
            std::advance(begin, 1);
        }

        return encode_type;
    }
};

struct file_utf_encode_detector {
    static utf_encode_t detect(std::FILE* p_file) noexcept {
        std::array<uint8_t, 4> bytes {{}};
        bytes.fill(0xFFu);
        for (int i = 0; i < 4; i++) {
            char byte = 0;
            const std::size_t size = std::fread(&byte, sizeof(char), 1, p_file);
            if (size != sizeof(char)) {
                break;
            }
            bytes[i] = static_cast<uint8_t>(byte & 0xFF);
        }

        bool has_bom = false;
        const utf_encode_t encode_type = detect_encoding_type(bytes, has_bom);

        long offset = 0;
        if (has_bom) {
            switch (encode_type) {
            case utf_encode_t::UTF_8:
                offset = 3;
                break;
            case utf_encode_t::UTF_16BE:
            case utf_encode_t::UTF_16LE:
                offset = 2;
                break;
            case utf_encode_t::UTF_32BE:
            case utf_encode_t::UTF_32LE:
                offset = 4;
                break;
            }
        }
        std::fseek(p_file, offset, SEEK_SET);

        return encode_type;
    }
};

struct stream_utf_encode_detector {
    static utf_encode_t detect(std::istream& is) noexcept {
        std::array<uint8_t, 4> bytes {{}};
        bytes.fill(0xFFu);
        for (int i = 0; i < 4; i++) {
            char ch = 0;
            is.read(&ch, 1);
            const std::streamsize size = is.gcount();
            if (size != 1) {
                is.clear();
                break;
            }
            bytes[i] = static_cast<uint8_t>(ch & 0xFF);
        }

        bool has_bom = false;
        const utf_encode_t encode_type = detect_encoding_type(bytes, has_bom);

        std::streamoff offset = 0;
        if (has_bom) {
            switch (encode_type) {
            case utf_encode_t::UTF_8:
                offset = 3;
                break;
            case utf_encode_t::UTF_16BE:
            case utf_encode_t::UTF_16LE:
                offset = 2;
                break;
            case utf_encode_t::UTF_32BE:
            case utf_encode_t::UTF_32LE:
                offset = 4;
                break;
            }
        }
        is.seekg(offset, std::ios_base::beg);

        return encode_type;
    }
};

FK_YAML_DETAIL_NAMESPACE_END

#endif

FK_YAML_DETAIL_NAMESPACE_BEGIN

template <typename IterType, typename = void>
class iterator_input_adapter;

template <typename IterType>
class iterator_input_adapter<IterType, enable_if_t<is_iterator_of<IterType, char>::value>> {
public:
    iterator_input_adapter() = default;

    iterator_input_adapter(IterType begin, IterType end, utf_encode_t encode_type, bool is_contiguous) noexcept
        : m_begin(begin),
          m_end(end),
          m_encode_type(encode_type),
          m_is_contiguous(is_contiguous) {
    }

    iterator_input_adapter(const iterator_input_adapter&) = delete;
    iterator_input_adapter(iterator_input_adapter&& rhs) = default;
    iterator_input_adapter& operator=(const iterator_input_adapter&) = delete;
    iterator_input_adapter& operator=(iterator_input_adapter&&) = default;
    ~iterator_input_adapter() = default;

    str_view get_buffer_view() {
        if FK_YAML_UNLIKELY (m_begin == m_end) {
            return {};
        }

        m_buffer.clear();

        switch (m_encode_type) {
        case utf_encode_t::UTF_8:
            return get_buffer_view_utf8();
        case utf_encode_t::UTF_16BE:
        case utf_encode_t::UTF_16LE:
            return get_buffer_view_utf16();
        case utf_encode_t::UTF_32BE:
        case utf_encode_t::UTF_32LE:
            return get_buffer_view_utf32();
        default:
            detail::unreachable();
        }
    }

private:
    str_view get_buffer_view_utf8() {
        FK_YAML_ASSERT(m_encode_type == utf_encode_t::UTF_8);

        IterType current = m_begin;
        std::deque<IterType> cr_itrs {};

        const auto next_byte = [&](uint8_t lead) {
            if FK_YAML_UNLIKELY (++current == m_end) {
                throw fkyaml::invalid_encoding("Truncated UTF-8 encoding.", {lead});
            }
            return static_cast<uint8_t>(*current);
        };

        while (current != m_end) {
            const auto first = static_cast<uint8_t>(*current);
            const uint32_t num_bytes = utf8::get_num_bytes(first);

            switch (num_bytes) {
            case 1:
                if FK_YAML_UNLIKELY (first == 0x0D) {
                    cr_itrs.emplace_back(current);
                }
                break;
            case 2: {
                const auto second = next_byte(first);
                const bool is_valid = utf8::validate(first, second);
                if FK_YAML_UNLIKELY (!is_valid) {
                    throw fkyaml::invalid_encoding("Invalid UTF-8 encoding.", {first, second});
                }
                break;
            }
            case 3: {
                const auto second = next_byte(first);
                const auto third = next_byte(first);
                const bool is_valid = utf8::validate(first, second, third);
                if FK_YAML_UNLIKELY (!is_valid) {
                    throw fkyaml::invalid_encoding("Invalid UTF-8 encoding.", {first, second, third});
                }
                break;
            }
            case 4: {
                const auto second = next_byte(first);
                const auto third = next_byte(first);
                const auto fourth = next_byte(first);
                const bool is_valid = utf8::validate(first, second, third, fourth);
                if FK_YAML_UNLIKELY (!is_valid) {
                    throw fkyaml::invalid_encoding("Invalid UTF-8 encoding.", {first, second, third, fourth});
                }
                break;
            }
            default:
                unreachable();
            }

            ++current;
        }

        const bool is_contiguous_no_cr = cr_itrs.empty() && m_is_contiguous;
        if FK_YAML_LIKELY (is_contiguous_no_cr) {
            FK_YAML_ASSERT(m_begin != m_end);
            return str_view {&*m_begin, static_cast<std::size_t>(std::distance(m_begin, m_end))};
        }

        m_buffer.reserve(std::distance(m_begin, m_end) - cr_itrs.size());

        current = m_begin;
        for (const auto& cr_itr : cr_itrs) {
            m_buffer.append(current, cr_itr);
            current = std::next(cr_itr);
        }
        m_buffer.append(current, m_end);

        return str_view {m_buffer.begin(), m_buffer.end()};
    }

    str_view get_buffer_view_utf16() {
        FK_YAML_ASSERT(m_encode_type == utf_encode_t::UTF_16BE || m_encode_type == utf_encode_t::UTF_16LE);

        m_buffer.reserve(std::distance(m_begin, m_end) / 2);

        int shift_bits[2] {0, 0};
        if (m_encode_type == utf_encode_t::UTF_16BE) {
            shift_bits[0] = 8;
        }
        else
        {
            shift_bits[1] = 8;
        }

        std::array<char16_t, 2> encoded_buffer {{0, 0}};
        uint32_t encoded_buf_size {0};
        std::array<uint8_t, 4> utf8_buffer {{0, 0, 0, 0}};
        uint32_t utf8_buf_size {0};

        IterType current = m_begin;
        while (current != m_end || encoded_buf_size != 0) {
            while (current != m_end && encoded_buf_size < 2) {
                const auto high = static_cast<uint8_t>(*current);
                auto utf16 = static_cast<char16_t>(high << shift_bits[0]);
                if FK_YAML_UNLIKELY (++current == m_end) {
                    throw fkyaml::invalid_encoding("Truncated UTF-16 encoding.", {high});
                }
                utf16 |= static_cast<char16_t>(static_cast<uint8_t>(*current) << shift_bits[1]);
                ++current;

                if FK_YAML_LIKELY (utf16 != char16_t(0x000Du)) {
                    encoded_buffer[encoded_buf_size++] = utf16;
                }
            }

            uint32_t consumed_size = 0;
            utf8::from_utf16(encoded_buffer, utf8_buffer, consumed_size, utf8_buf_size);

            if FK_YAML_LIKELY (consumed_size == 1) {
                encoded_buffer[0] = encoded_buffer[1];
            }
            encoded_buf_size -= consumed_size;

            m_buffer.append(reinterpret_cast<const char*>(utf8_buffer.data()), utf8_buf_size);
        }

        return str_view {m_buffer.begin(), m_buffer.end()};
    }

    str_view get_buffer_view_utf32() {
        FK_YAML_ASSERT(m_encode_type == utf_encode_t::UTF_32BE || m_encode_type == utf_encode_t::UTF_32LE);

        m_buffer.reserve(std::distance(m_begin, m_end) / 4);

        int shift_bits[4] {0, 0, 0, 0};
        if (m_encode_type == utf_encode_t::UTF_32BE) {
            shift_bits[0] = 24;
            shift_bits[1] = 16;
            shift_bits[2] = 8;
        }
        else
        {
            shift_bits[1] = 8;
            shift_bits[2] = 16;
            shift_bits[3] = 24;
        }

        std::array<uint8_t, 4> utf8_buffer {{0, 0, 0, 0}};
        uint32_t utf8_buf_size {0};

        IterType current = m_begin;
        while (current != m_end) {
            uint32_t utf32_bits = 0;
            for (int i = 0; i < 4; i++) {
                if FK_YAML_UNLIKELY (current == m_end) {
                    throw fkyaml::invalid_encoding("Truncated UTF-32 encoding.", static_cast<char32_t>(utf32_bits));
                }
                utf32_bits |= static_cast<uint32_t>(static_cast<uint8_t>(*current)) << shift_bits[i];
                ++current;
            }
            const auto utf32 = static_cast<char32_t>(utf32_bits);

            if FK_YAML_LIKELY (utf32 != char32_t(0x0000000Du)) {
                utf8::from_utf32(utf32, utf8_buffer, utf8_buf_size);
                m_buffer.append(reinterpret_cast<const char*>(utf8_buffer.data()), utf8_buf_size);
            }
        }

        return str_view {m_buffer.begin(), m_buffer.end()};
    }

private:
    IterType m_begin {};
    IterType m_end {};
    utf_encode_t m_encode_type {utf_encode_t::UTF_8};
    std::string m_buffer;
    bool m_is_contiguous {false};
};

#if FK_YAML_HAS_CHAR8_T

template <typename IterType>
class iterator_input_adapter<IterType, enable_if_t<is_iterator_of<IterType, char8_t>::value>> {
public:
    iterator_input_adapter() = default;

    iterator_input_adapter(IterType begin, IterType end, utf_encode_t encode_type, bool is_contiguous) noexcept
        : m_begin(begin),
          m_end(end),
          m_encode_type(encode_type),
          m_is_contiguous(is_contiguous) {
        FK_YAML_ASSERT(m_encode_type == utf_encode_t::UTF_8);
    }

    iterator_input_adapter(const iterator_input_adapter&) = delete;
    iterator_input_adapter(iterator_input_adapter&& rhs) = default;
    iterator_input_adapter& operator=(const iterator_input_adapter&) = delete;
    iterator_input_adapter& operator=(iterator_input_adapter&&) = default;
    ~iterator_input_adapter() = default;

    str_view get_buffer_view() {
        if FK_YAML_UNLIKELY (m_begin == m_end) {
            return {};
        }

        IterType current = m_begin;
        std::deque<IterType> cr_itrs {};

        const auto next_byte = [&](uint8_t lead) {
            if FK_YAML_UNLIKELY (++current == m_end) {
                throw fkyaml::invalid_encoding("Truncated UTF-8 encoding.", {lead});
            }
            return static_cast<uint8_t>(*current);
        };

        while (current != m_end) {
            const auto first = static_cast<uint8_t>(*current);
            const uint32_t num_bytes = utf8::get_num_bytes(first);

            switch (num_bytes) {
            case 1:
                if FK_YAML_UNLIKELY (first == 0x0D) {
                    cr_itrs.emplace_back(current);
                }
                break;
            case 2: {
                const auto second = next_byte(first);
                const bool is_valid = utf8::validate(first, second);
                if FK_YAML_UNLIKELY (!is_valid) {
                    throw fkyaml::invalid_encoding("Invalid UTF-8 encoding.", {first, second});
                }
                break;
            }
            case 3: {
                const auto second = next_byte(first);
                const auto third = next_byte(first);
                const bool is_valid = utf8::validate(first, second, third);
                if FK_YAML_UNLIKELY (!is_valid) {
                    throw fkyaml::invalid_encoding("Invalid UTF-8 encoding.", {first, second, third});
                }
                break;
            }
            case 4: {
                const auto second = next_byte(first);
                const auto third = next_byte(first);
                const auto fourth = next_byte(first);
                const bool is_valid = utf8::validate(first, second, third, fourth);
                if FK_YAML_UNLIKELY (!is_valid) {
                    throw fkyaml::invalid_encoding("Invalid UTF-8 encoding.", {first, second, third, fourth});
                }
                break;
            }
            default:
                unreachable();
            }

            ++current;
        }

        m_buffer.reserve(std::distance(m_begin, m_end) - cr_itrs.size());
        current = m_begin;
        for (const auto& cr_itr : cr_itrs) {
            std::transform(
                current, cr_itr, std::back_inserter(m_buffer), [](char8_t c) { return static_cast<char>(c); });
            current = std::next(cr_itr);
        }
        std::transform(current, m_end, std::back_inserter(m_buffer), [](char8_t c) { return static_cast<char>(c); });

        return str_view {m_buffer.begin(), m_buffer.end()};
    }

private:
    IterType m_begin {};
    IterType m_end {};
    utf_encode_t m_encode_type {utf_encode_t::UTF_8};
    std::string m_buffer;
    bool m_is_contiguous {false};
};

#endif

template <typename IterType>
class iterator_input_adapter<IterType, enable_if_t<is_iterator_of<IterType, char16_t>::value>> {
public:
    iterator_input_adapter() = default;

    iterator_input_adapter(IterType begin, IterType end, utf_encode_t encode_type, bool is_contiguous) noexcept
        : m_begin(begin),
          m_end(end),
          m_encode_type(encode_type),
          m_is_contiguous(is_contiguous) {
        FK_YAML_ASSERT(m_encode_type == utf_encode_t::UTF_16BE || m_encode_type == utf_encode_t::UTF_16LE);
    }

    iterator_input_adapter(const iterator_input_adapter&) = delete;
    iterator_input_adapter(iterator_input_adapter&& rhs) = default;
    iterator_input_adapter& operator=(const iterator_input_adapter&) = delete;
    iterator_input_adapter& operator=(iterator_input_adapter&&) = default;
    ~iterator_input_adapter() = default;

    str_view get_buffer_view() {
        if FK_YAML_UNLIKELY (m_begin == m_end) {
            return {};
        }

        const int shift_bits = (m_encode_type == utf_encode_t::UTF_16BE) ? 0 : 8;

        std::array<char16_t, 2> encoded_buffer {{0, 0}};
        uint32_t encoded_buf_size {0};
        std::array<uint8_t, 4> utf8_buffer {{0, 0, 0, 0}};
        uint32_t utf8_buf_size {0};

        m_buffer.reserve(std::distance(m_begin, m_end));

        IterType current = m_begin;
        while (current != m_end || encoded_buf_size != 0) {
            while (current != m_end && encoded_buf_size < 2) {
                char16_t utf16 = *current;
                ++current;
                utf16 = static_cast<char16_t>(((utf16 & 0x00FFu) << shift_bits) | ((utf16 & 0xFF00u) >> shift_bits));

                if FK_YAML_LIKELY (utf16 != char16_t(0x000Du)) {
                    encoded_buffer[encoded_buf_size++] = utf16;
                }
            }

            uint32_t consumed_size = 0;
            utf8::from_utf16(encoded_buffer, utf8_buffer, consumed_size, utf8_buf_size);

            if FK_YAML_LIKELY (consumed_size == 1) {
                encoded_buffer[0] = encoded_buffer[1];
                encoded_buffer[1] = 0;
            }
            encoded_buf_size -= consumed_size;

            m_buffer.append(reinterpret_cast<const char*>(utf8_buffer.data()), utf8_buf_size);
        }

        return str_view {m_buffer.begin(), m_buffer.end()};
    }

private:
    IterType m_begin {};
    IterType m_end {};
    utf_encode_t m_encode_type {utf_encode_t::UTF_16BE};
    std::string m_buffer;
    bool m_is_contiguous {false};
};

template <typename IterType>
class iterator_input_adapter<IterType, enable_if_t<is_iterator_of<IterType, char32_t>::value>> {
public:
    iterator_input_adapter() = default;

    iterator_input_adapter(IterType begin, IterType end, utf_encode_t encode_type, bool is_contiguous) noexcept
        : m_begin(begin),
          m_end(end),
          m_encode_type(encode_type),
          m_is_contiguous(is_contiguous) {
        FK_YAML_ASSERT(m_encode_type == utf_encode_t::UTF_32BE || m_encode_type == utf_encode_t::UTF_32LE);
    }

    iterator_input_adapter(const iterator_input_adapter&) = delete;
    iterator_input_adapter(iterator_input_adapter&& rhs) = default;
    iterator_input_adapter& operator=(const iterator_input_adapter&) = delete;
    iterator_input_adapter& operator=(iterator_input_adapter&&) = default;
    ~iterator_input_adapter() = default;

    str_view get_buffer_view() {
        if FK_YAML_UNLIKELY (m_begin == m_end) {
            return {};
        }

        int shift_bits[4] {0, 0, 0, 0};
        if (m_encode_type == utf_encode_t::UTF_32LE) {
            shift_bits[0] = 24;
            shift_bits[1] = 8;
            shift_bits[2] = 8;
            shift_bits[3] = 24;
        }

        std::array<uint8_t, 4> utf8_buffer {{0, 0, 0, 0}};
        uint32_t utf8_buf_size {0};

        m_buffer.reserve(std::distance(m_begin, m_end));

        IterType current = m_begin;
        while (current != m_end) {
            const char32_t tmp = *current;
            ++current;
            const auto utf32 = static_cast<char32_t>(
                ((tmp & 0xFF000000u) >> shift_bits[0]) | ((tmp & 0x00FF0000u) >> shift_bits[1]) |
                ((tmp & 0x0000FF00u) << shift_bits[2]) | ((tmp & 0x000000FFu) << shift_bits[3]));

            if FK_YAML_UNLIKELY (utf32 != static_cast<char32_t>(0x0000000Du)) {
                utf8::from_utf32(utf32, utf8_buffer, utf8_buf_size);
                m_buffer.append(reinterpret_cast<const char*>(utf8_buffer.data()), utf8_buf_size);
            }
        }

        return str_view {m_buffer.begin(), m_buffer.end()};
    }

private:
    IterType m_begin {};
    IterType m_end {};
    utf_encode_t m_encode_type {utf_encode_t::UTF_32BE};
    std::string m_buffer;
    bool m_is_contiguous {false};
};

class file_input_adapter {
public:
    file_input_adapter() = default;

    explicit file_input_adapter(std::FILE* file, utf_encode_t encode_type) noexcept
        : m_file(file),
          m_encode_type(encode_type) {
    }

    file_input_adapter(const file_input_adapter&) = delete;
    file_input_adapter(file_input_adapter&& rhs) = default;
    file_input_adapter& operator=(const file_input_adapter&) = delete;
    file_input_adapter& operator=(file_input_adapter&&) = default;
    ~file_input_adapter() = default;

    str_view get_buffer_view() {
        switch (m_encode_type) {
        case utf_encode_t::UTF_8:
            return get_buffer_view_utf8();
        case utf_encode_t::UTF_16BE:
        case utf_encode_t::UTF_16LE:
            return get_buffer_view_utf16();
        case utf_encode_t::UTF_32BE:
        case utf_encode_t::UTF_32LE:
            return get_buffer_view_utf32();
        default:
            detail::unreachable();
        }
    }

private:
    str_view get_buffer_view_utf8() {
        FK_YAML_ASSERT(m_encode_type == utf_encode_t::UTF_8);

        m_buffer.clear();
        char tmp_buf[256] {};
        constexpr std::size_t buf_size = sizeof(tmp_buf) / sizeof(tmp_buf[0]);
        std::size_t read_size = 0;
        while ((read_size = std::fread(&tmp_buf[0], sizeof(char), buf_size, m_file)) > 0) {
            char* p_current = &tmp_buf[0];
            char* p_end = p_current + read_size;

            char* p_cr = p_current;
            do {
                if FK_YAML_UNLIKELY (*p_cr == '\r') {
                    m_buffer.append(p_current, p_cr);
                    p_current = p_cr + 1;
                }
                ++p_cr;
            } while (p_cr != p_end);

            m_buffer.append(p_current, p_end);
        }

        if FK_YAML_UNLIKELY (m_buffer.empty()) {
            return {};
        }

        auto current = m_buffer.begin();
        auto end = m_buffer.end();

        const auto next_byte = [&](uint8_t lead) {
            if FK_YAML_UNLIKELY (current == end) {
                throw fkyaml::invalid_encoding("Truncated UTF-8 encoding.", {lead});
            }
            return static_cast<uint8_t>(*current++);
        };

        while (current != end) {
            const auto first = static_cast<uint8_t>(*current++);
            const uint32_t num_bytes = utf8::get_num_bytes(first);

            switch (num_bytes) {
            case 1:
                break;
            case 2: {
                const auto second = next_byte(first);
                const bool is_valid = utf8::validate(first, second);
                if FK_YAML_UNLIKELY (!is_valid) {
                    throw fkyaml::invalid_encoding("Invalid UTF-8 encoding.", {first, second});
                }
                break;
            }
            case 3: {
                const auto second = next_byte(first);
                const auto third = next_byte(first);
                const bool is_valid = utf8::validate(first, second, third);
                if FK_YAML_UNLIKELY (!is_valid) {
                    throw fkyaml::invalid_encoding("Invalid UTF-8 encoding.", {first, second, third});
                }
                break;
            }
            case 4: {
                const auto second = next_byte(first);
                const auto third = next_byte(first);
                const auto fourth = next_byte(first);
                const bool is_valid = utf8::validate(first, second, third, fourth);
                if FK_YAML_UNLIKELY (!is_valid) {
                    throw fkyaml::invalid_encoding("Invalid UTF-8 encoding.", {first, second, third, fourth});
                }
                break;
            }
            default:
                unreachable();
            }
        }

        return str_view {m_buffer.begin(), m_buffer.end()};
    }

    str_view get_buffer_view_utf16() {
        FK_YAML_ASSERT(m_encode_type == utf_encode_t::UTF_16BE || m_encode_type == utf_encode_t::UTF_16LE);

        int shift_bits[2] {0, 0};
        if (m_encode_type == utf_encode_t::UTF_16BE) {
            shift_bits[0] = 8;
        }
        else {
            shift_bits[1] = 8;
        }

        char chars[2] = {0, 0};
        std::array<char16_t, 2> encoded_buffer {{0, 0}};
        uint32_t encoded_buf_size {0};
        std::array<uint8_t, 4> utf8_buffer {{0, 0, 0, 0}};
        uint32_t utf8_buf_size {0};

        while (std::feof(m_file) == 0) {
            while (encoded_buf_size < 2 && std::fread(&chars[0], sizeof(char), 2, m_file) == 2) {
                const auto utf16 = static_cast<char16_t>(
                    (static_cast<uint8_t>(chars[0]) << shift_bits[0]) |
                    (static_cast<uint8_t>(chars[1]) << shift_bits[1]));
                if FK_YAML_LIKELY (utf16 != static_cast<char16_t>(0x000Du)) {
                    encoded_buffer[encoded_buf_size++] = utf16;
                }
            }

            uint32_t consumed_size = 0;
            utf8::from_utf16(encoded_buffer, utf8_buffer, consumed_size, utf8_buf_size);

            if FK_YAML_LIKELY (consumed_size == 1) {
                encoded_buffer[0] = encoded_buffer[1];
            }
            encoded_buf_size -= consumed_size;

            m_buffer.append(reinterpret_cast<const char*>(utf8_buffer.data()), utf8_buf_size);
        }

        return str_view {m_buffer.begin(), m_buffer.end()};
    }

    str_view get_buffer_view_utf32() {
        FK_YAML_ASSERT(m_encode_type == utf_encode_t::UTF_32BE || m_encode_type == utf_encode_t::UTF_32LE);

        int shift_bits[4] {0, 0, 0, 0};
        if (m_encode_type == utf_encode_t::UTF_32BE) {
            shift_bits[0] = 24;
            shift_bits[1] = 16;
            shift_bits[2] = 8;
        }
        else {
            shift_bits[1] = 8;
            shift_bits[2] = 16;
            shift_bits[3] = 24;
        }

        char chars[4] = {0, 0, 0, 0};
        std::array<uint8_t, 4> utf8_buffer {{0, 0, 0, 0}};
        uint32_t utf8_buf_size {0};

        while (std::feof(m_file) == 0) {
            const std::size_t size = std::fread(&chars[0], sizeof(char), 4, m_file);
            if (size != 4) {
                break;
            }

            const auto utf32 = static_cast<char32_t>(
                (static_cast<uint8_t>(chars[0]) << shift_bits[0]) | (static_cast<uint8_t>(chars[1]) << shift_bits[1]) |
                (static_cast<uint8_t>(chars[2]) << shift_bits[2]) | (static_cast<uint8_t>(chars[3]) << shift_bits[3]));

            if FK_YAML_LIKELY (utf32 != char32_t(0x0000000Du)) {
                utf8::from_utf32(utf32, utf8_buffer, utf8_buf_size);
                m_buffer.append(reinterpret_cast<const char*>(utf8_buffer.data()), utf8_buf_size);
            }
        }

        return str_view {m_buffer.begin(), m_buffer.end()};
    }

private:
    std::FILE* m_file {nullptr};
    utf_encode_t m_encode_type {utf_encode_t::UTF_8};
    std::string m_buffer;
};

class stream_input_adapter {
public:
    stream_input_adapter() = default;

    explicit stream_input_adapter(std::istream& is, utf_encode_t encode_type) noexcept
        : m_istream(&is),
          m_encode_type(encode_type) {
    }

    stream_input_adapter(const stream_input_adapter&) = delete;
    stream_input_adapter& operator=(const stream_input_adapter&) = delete;
    stream_input_adapter(stream_input_adapter&&) = default;
    stream_input_adapter& operator=(stream_input_adapter&&) = default;
    ~stream_input_adapter() = default;

    str_view get_buffer_view() {
        switch (m_encode_type) {
        case utf_encode_t::UTF_8:
            return get_buffer_view_utf8();
        case utf_encode_t::UTF_16BE:
        case utf_encode_t::UTF_16LE:
            return get_buffer_view_utf16();
        case utf_encode_t::UTF_32BE:
        case utf_encode_t::UTF_32LE:
            return get_buffer_view_utf32();
        default:
            detail::unreachable();
        }
    }

private:
    str_view get_buffer_view_utf8() {
        FK_YAML_ASSERT(m_encode_type == utf_encode_t::UTF_8);

        m_buffer.clear();
        char tmp_buf[256] {};
        do {
            m_istream->read(&tmp_buf[0], 256);
            const auto read_size = static_cast<std::size_t>(m_istream->gcount());
            if FK_YAML_UNLIKELY (read_size == 0) {
                break;
            }

            char* p_current = &tmp_buf[0];
            char* p_end = p_current + read_size;

            char* p_cr = p_current;
            do {
                if FK_YAML_UNLIKELY (*p_cr == '\r') {
                    m_buffer.append(p_current, p_cr);
                    p_current = p_cr + 1;
                }
                ++p_cr;
            } while (p_cr != p_end);

            m_buffer.append(p_current, p_end);
        } while (!m_istream->eof());

        if FK_YAML_UNLIKELY (m_buffer.empty()) {
            return {};
        }

        auto current = m_buffer.begin();
        auto end = m_buffer.end();

        const auto next_byte = [&](uint8_t lead) {
            if FK_YAML_UNLIKELY (current == end) {
                throw fkyaml::invalid_encoding("Truncated UTF-8 encoding.", {lead});
            }
            return static_cast<uint8_t>(*current++);
        };

        while (current != end) {
            const auto first = static_cast<uint8_t>(*current++);
            const uint32_t num_bytes = utf8::get_num_bytes(first);

            switch (num_bytes) {
            case 1:
                break;
            case 2: {
                const auto second = next_byte(first);
                const bool is_valid = utf8::validate(first, second);
                if FK_YAML_UNLIKELY (!is_valid) {
                    throw fkyaml::invalid_encoding("Invalid UTF-8 encoding.", {first, second});
                }
                break;
            }
            case 3: {
                const auto second = next_byte(first);
                const auto third = next_byte(first);
                const bool is_valid = utf8::validate(first, second, third);
                if FK_YAML_UNLIKELY (!is_valid) {
                    throw fkyaml::invalid_encoding("Invalid UTF-8 encoding.", {first, second, third});
                }
                break;
            }
            case 4: {
                const auto second = next_byte(first);
                const auto third = next_byte(first);
                const auto fourth = next_byte(first);
                const bool is_valid = utf8::validate(first, second, third, fourth);
                if FK_YAML_UNLIKELY (!is_valid) {
                    throw fkyaml::invalid_encoding("Invalid UTF-8 encoding.", {first, second, third, fourth});
                }
                break;
            }
            default:
                unreachable();
            }
        }

        return str_view {m_buffer.begin(), m_buffer.end()};
    }

    str_view get_buffer_view_utf16() {
        FK_YAML_ASSERT(m_encode_type == utf_encode_t::UTF_16BE || m_encode_type == utf_encode_t::UTF_16LE);

        int shift_bits[2] {0, 0};
        if (m_encode_type == utf_encode_t::UTF_16BE) {
            shift_bits[0] = 8;
        }
        else {
            shift_bits[1] = 8;
        }

        char chars[2] = {0, 0};
        std::array<char16_t, 2> encoded_buffer {{0, 0}};
        uint32_t encoded_buf_size {0};
        std::array<uint8_t, 4> utf8_buffer {{0, 0, 0, 0}};
        uint32_t utf8_buf_size {0};

        do {
            while (encoded_buf_size < 2) {
                m_istream->read(&chars[0], 2);
                const std::streamsize size = m_istream->gcount();
                if FK_YAML_UNLIKELY (size != 2) {
                    break;
                }

                const auto utf16 = static_cast<char16_t>(
                    (static_cast<uint8_t>(chars[0]) << shift_bits[0]) |
                    (static_cast<uint8_t>(chars[1]) << shift_bits[1]));

                if FK_YAML_LIKELY (utf16 != static_cast<char16_t>(0x000Du)) {
                    encoded_buffer[encoded_buf_size++] = utf16;
                }
            }

            uint32_t consumed_size = 0;
            utf8::from_utf16(encoded_buffer, utf8_buffer, consumed_size, utf8_buf_size);

            if FK_YAML_LIKELY (consumed_size == 1) {
                encoded_buffer[0] = encoded_buffer[1];
            }
            encoded_buf_size -= consumed_size;

            m_buffer.append(reinterpret_cast<const char*>(utf8_buffer.data()), utf8_buf_size);
        } while (!m_istream->eof());

        return str_view {m_buffer.begin(), m_buffer.end()};
    }

    str_view get_buffer_view_utf32() {
        FK_YAML_ASSERT(m_encode_type == utf_encode_t::UTF_32BE || m_encode_type == utf_encode_t::UTF_32LE);

        int shift_bits[4] {0, 0, 0, 0};
        if (m_encode_type == utf_encode_t::UTF_32BE) {
            shift_bits[0] = 24;
            shift_bits[1] = 16;
            shift_bits[2] = 8;
        }
        else {
            shift_bits[1] = 8;
            shift_bits[2] = 16;
            shift_bits[3] = 24;
        }

        char chars[4] = {0, 0, 0, 0};
        std::array<uint8_t, 4> utf8_buffer {{0, 0, 0, 0}};
        uint32_t utf8_buf_size {0};

        do {
            m_istream->read(&chars[0], 4);
            const std::streamsize size = m_istream->gcount();
            if FK_YAML_UNLIKELY (size != 4) {
                break;
            }

            const auto utf32 = static_cast<char32_t>(
                (static_cast<uint8_t>(chars[0]) << shift_bits[0]) | (static_cast<uint8_t>(chars[1]) << shift_bits[1]) |
                (static_cast<uint8_t>(chars[2]) << shift_bits[2]) | (static_cast<uint8_t>(chars[3]) << shift_bits[3]));

            if FK_YAML_LIKELY (utf32 != char32_t(0x0000000Du)) {
                utf8::from_utf32(utf32, utf8_buffer, utf8_buf_size);
                m_buffer.append(reinterpret_cast<const char*>(utf8_buffer.data()), utf8_buf_size);
            }
        } while (!m_istream->eof());

        return str_view {m_buffer.begin(), m_buffer.end()};
    }

private:
    std::istream* m_istream {nullptr};
    utf_encode_t m_encode_type {utf_encode_t::UTF_8};
    std::string m_buffer;
};

template <typename ItrType>
inline iterator_input_adapter<ItrType> create_iterator_input_adapter(ItrType begin, ItrType end, bool is_contiguous) {
    const utf_encode_t encode_type = utf_encode_detector<ItrType>::detect(begin, end);
    return iterator_input_adapter<ItrType>(begin, end, encode_type, is_contiguous);
}

template <typename ItrType>
inline iterator_input_adapter<ItrType> input_adapter(ItrType begin, ItrType end) {
    bool is_contiguous = true;
    const auto size = std::distance(begin, end);

    if FK_YAML_LIKELY (size > 0) {
        using char_ptr_t = remove_cvref_t<typename std::iterator_traits<ItrType>::pointer>;
        char_ptr_t p_begin = &*begin;
        char_ptr_t p_second_last = &*std::next(begin, size - 1);
        is_contiguous = (p_second_last - p_begin == size);
    }
    return create_iterator_input_adapter(begin, end, is_contiguous);
}

template <typename T, std::size_t N>
inline auto input_adapter(T (&array)[N]) -> decltype(create_iterator_input_adapter(array, array + (N - 1), true)) {
    return create_iterator_input_adapter(array, array + (N - 1), true);
}

namespace input_adapter_factory {

using std::begin;
using std::end;

template <typename ContainerType, typename = void>
struct container_input_adapter_factory {};

template <typename ContainerType>
struct container_input_adapter_factory<
    ContainerType, void_t<decltype(begin(std::declval<ContainerType>()), end(std::declval<ContainerType>()))>> {
    using adapter_type =
        decltype(input_adapter(begin(std::declval<ContainerType>()), end(std::declval<ContainerType>())));

    static adapter_type create(const ContainerType& container) {
        return input_adapter(begin(container), end(container));
    }
};

}

template <typename ContainerType>
inline typename input_adapter_factory::container_input_adapter_factory<ContainerType>::adapter_type input_adapter(
    const ContainerType& container) {
    return input_adapter_factory::container_input_adapter_factory<ContainerType>::create(container);
}

inline file_input_adapter input_adapter(std::FILE* file) {
    if FK_YAML_UNLIKELY (!file) {
        throw fkyaml::exception("Invalid FILE object pointer.");
    }

    const utf_encode_t encode_type = file_utf_encode_detector::detect(file);
    return file_input_adapter(file, encode_type);
}

inline stream_input_adapter input_adapter(std::istream& stream) {
    if FK_YAML_UNLIKELY (!stream.good()) {
        throw fkyaml::exception("Invalid stream.");
    }

    const utf_encode_t encode_type = stream_utf_encode_detector::detect(stream);
    return stream_input_adapter(stream, encode_type);
}

FK_YAML_DETAIL_NAMESPACE_END

#endif

#ifndef FK_YAML_DETAIL_ITERATOR_HPP
#define FK_YAML_DETAIL_ITERATOR_HPP

#include <cstddef>
#include <iterator>

FK_YAML_DETAIL_NAMESPACE_BEGIN

template <typename ValueType>
struct iterator_traits {
    using value_type = typename ValueType::value_type;
    using difference_type = typename ValueType::difference_type;
    using pointer = typename ValueType::pointer;
    using reference = typename ValueType::reference;
};

template <typename ValueType>
struct iterator_traits<const ValueType> {
    using value_type = typename ValueType::value_type;
    using difference_type = typename ValueType::difference_type;
    using pointer = typename ValueType::const_pointer;
    using reference = typename ValueType::const_reference;
};

enum class iterator_t : std::uint8_t {
    SEQUENCE,
    MAPPING,
};

template <typename BasicNodeType>
struct iterator_holder {
    static_assert(
        is_basic_node<BasicNodeType>::value,
        "iterator_holder class only accepts a basic_node as its template parameter.");

    typename BasicNodeType::sequence_type::iterator sequence_iterator {};
    typename BasicNodeType::mapping_type::iterator mapping_iterator {};
};

template <typename ValueType>
class iterator {
    using other_iterator_type = typename std::conditional<
        std::is_const<ValueType>::value, iterator<typename std::remove_const<ValueType>::type>,
        iterator<const ValueType>>::type;

    friend other_iterator_type;

public:
    using iterator_traits_type = iterator_traits<ValueType>;

    using iterator_category = std::bidirectional_iterator_tag;
    using value_type = typename iterator_traits_type::value_type;
    using difference_type = typename iterator_traits_type::difference_type;
    using pointer = typename iterator_traits_type::pointer;
    using reference = typename iterator_traits_type::reference;

    static_assert(is_basic_node<value_type>::value, "iterator class only accepts a basic_node as its value type.");

    iterator() = default;

    iterator(const typename value_type::sequence_type::iterator& itr) noexcept {
        m_iterator_holder.sequence_iterator = itr;
    }

    iterator(const typename value_type::mapping_type::iterator& itr) noexcept
        : m_inner_iterator_type(iterator_t::MAPPING) {
        m_iterator_holder.mapping_iterator = itr;
    }

    iterator(const iterator&) = default;

    template <
        typename OtherIterator,
        enable_if_t<
            conjunction<std::is_same<OtherIterator, other_iterator_type>, std::is_const<ValueType>>::value, int> = 0>
    iterator(const OtherIterator& other) noexcept
        : m_inner_iterator_type(other.m_inner_iterator_type),
          m_iterator_holder(other.m_iterator_holder) {
    }

    iterator& operator=(const iterator&) = default;

    template <
        typename OtherIterator,
        enable_if_t<
            conjunction<std::is_same<OtherIterator, other_iterator_type>, std::is_const<ValueType>>::value, int> = 0>
    iterator& operator=(const OtherIterator& other) noexcept {
        m_inner_iterator_type = other.m_inner_iterator_type;
        m_iterator_holder = other.m_iterator_holder;
        return *this;
    }

    iterator(iterator&&) = default;

    iterator& operator=(iterator&&) = default;

    ~iterator() = default;

    pointer operator->() noexcept {
        if (m_inner_iterator_type == iterator_t::SEQUENCE) {
            return &(*(m_iterator_holder.sequence_iterator));
        }

        return &(m_iterator_holder.mapping_iterator->second);
    }

    reference operator*() const noexcept {
        if (m_inner_iterator_type == iterator_t::SEQUENCE) {
            return *(m_iterator_holder.sequence_iterator);
        }

        return m_iterator_holder.mapping_iterator->second;
    }

    iterator& operator+=(difference_type i) noexcept {
        switch (m_inner_iterator_type) {
        case iterator_t::SEQUENCE:
            std::advance(m_iterator_holder.sequence_iterator, i);
            break;
        case iterator_t::MAPPING:
            std::advance(m_iterator_holder.mapping_iterator, i);
            break;
        }
        return *this;
    }

    iterator operator+(difference_type i) const noexcept {
        auto result = *this;
        result += i;
        return result;
    }

    iterator& operator++() noexcept {
        switch (m_inner_iterator_type) {
        case iterator_t::SEQUENCE:
            std::advance(m_iterator_holder.sequence_iterator, 1);
            break;
        case iterator_t::MAPPING:
            std::advance(m_iterator_holder.mapping_iterator, 1);
            break;
        }
        return *this;
    }

    iterator operator++(int) & noexcept {
        auto result = *this;
        ++(*this);
        return result;
    }

    iterator& operator-=(difference_type i) noexcept {
        return operator+=(-i);
    }

    iterator operator-(difference_type i) const noexcept {
        auto result = *this;
        result -= i;
        return result;
    }

    iterator& operator--() noexcept {
        switch (m_inner_iterator_type) {
        case iterator_t::SEQUENCE:
            std::advance(m_iterator_holder.sequence_iterator, -1);
            break;
        case iterator_t::MAPPING:
            std::advance(m_iterator_holder.mapping_iterator, -1);
            break;
        }
        return *this;
    }

    iterator operator--(int) & noexcept {
        auto result = *this;
        --(*this);
        return result;
    }

    template <
        typename Iterator,
        enable_if_t<
            disjunction<std::is_same<Iterator, iterator>, std::is_same<Iterator, other_iterator_type>>::value, int> = 0>
    bool operator==(const Iterator& rhs) const {
        if FK_YAML_UNLIKELY (m_inner_iterator_type != rhs.m_inner_iterator_type) {
            throw fkyaml::exception("Cannot compare iterators of different container types.");
        }

        if (m_inner_iterator_type == iterator_t::SEQUENCE) {
            return (m_iterator_holder.sequence_iterator == rhs.m_iterator_holder.sequence_iterator);
        }

        return (m_iterator_holder.mapping_iterator == rhs.m_iterator_holder.mapping_iterator);
    }

    template <
        typename Iterator,
        enable_if_t<
            disjunction<std::is_same<Iterator, iterator>, std::is_same<Iterator, other_iterator_type>>::value, int> = 0>
    bool operator!=(const Iterator& rhs) const {
        return !operator==(rhs);
    }

    template <
        typename Iterator,
        enable_if_t<
            disjunction<std::is_same<Iterator, iterator>, std::is_same<Iterator, other_iterator_type>>::value, int> = 0>
    bool operator<(const Iterator& rhs) const {
        if FK_YAML_UNLIKELY (m_inner_iterator_type != rhs.m_inner_iterator_type) {
            throw fkyaml::exception("Cannot compare iterators of different container types.");
        }

        if FK_YAML_UNLIKELY (m_inner_iterator_type == iterator_t::MAPPING) {
            throw fkyaml::exception("Cannot compare order of iterators of the mapping container type");
        }

        return (m_iterator_holder.sequence_iterator < rhs.m_iterator_holder.sequence_iterator);
    }

    template <
        typename Iterator,
        enable_if_t<
            disjunction<std::is_same<Iterator, iterator>, std::is_same<Iterator, other_iterator_type>>::value, int> = 0>
    bool operator<=(const Iterator& rhs) const {
        return !rhs.operator<(*this);
    }

    template <
        typename Iterator,
        enable_if_t<
            disjunction<std::is_same<Iterator, iterator>, std::is_same<Iterator, other_iterator_type>>::value, int> = 0>
    bool operator>(const Iterator& rhs) const {
        return !operator<=(rhs);
    }

    template <
        typename Iterator,
        enable_if_t<
            disjunction<std::is_same<Iterator, iterator>, std::is_same<Iterator, other_iterator_type>>::value, int> = 0>
    bool operator>=(const Iterator& rhs) const {
        return !operator<(rhs);
    }

public:
    iterator_t type() const noexcept {
        return m_inner_iterator_type;
    }

    const typename value_type::mapping_type::key_type& key() const {
        if FK_YAML_UNLIKELY (m_inner_iterator_type == iterator_t::SEQUENCE) {
            throw fkyaml::exception("Cannot retrieve key from non-mapping iterators.");
        }

        return m_iterator_holder.mapping_iterator->first;
    }

    reference value() const noexcept {
        return operator*();
    }

private:
    iterator_t m_inner_iterator_type {iterator_t::SEQUENCE};
    iterator_holder<value_type> m_iterator_holder {};
};

template <std::size_t I, typename ValueType, enable_if_t<I == 0, int> = 0>
inline auto get(const iterator<ValueType>& i) -> decltype(i.key()) {
    return i.key();
}

template <std::size_t I, typename ValueType, enable_if_t<I == 1, int> = 0>
inline auto get(const iterator<ValueType>& i) -> decltype(i.value()) {
    return i.value();
}

FK_YAML_DETAIL_NAMESPACE_END

namespace std {

#ifdef __clang__
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wmismatched-tags"
#endif

template <typename ValueType>
struct tuple_size<::fkyaml::detail::iterator<ValueType>> : integral_constant<size_t, 2> {};

template <size_t I, typename ValueType>
struct tuple_element<I, ::fkyaml::detail::iterator<ValueType>> {
    using type = decltype(get<I>(std::declval<::fkyaml::detail::iterator<ValueType>>()));
};

#ifdef __clang__
#pragma clang diagnostic pop
#endif

}

#endif

#ifndef FK_YAML_DETAIL_MAP_RANGE_PROXY_HPP
#define FK_YAML_DETAIL_MAP_RANGE_PROXY_HPP

FK_YAML_DETAIL_NAMESPACE_BEGIN

template <typename Iterator>
class map_iterator_proxy {
public:
    using value_type = Iterator;

    using difference_type = std::ptrdiff_t;

    using reference = value_type&;

    using pointer = value_type*;

    using iterator_category = std::forward_iterator_tag;

    map_iterator_proxy() = default;

    map_iterator_proxy(const Iterator& i) noexcept
        : m_base_iterator(i) {
    }

    map_iterator_proxy(const map_iterator_proxy&) = default;

    map_iterator_proxy& operator=(const map_iterator_proxy&) = default;

    map_iterator_proxy(map_iterator_proxy&&) = default;

    map_iterator_proxy& operator=(map_iterator_proxy&&) = default;

    ~map_iterator_proxy() = default;

    reference operator*() noexcept {
        return m_base_iterator;
    }

    pointer operator->() noexcept {
        return &m_base_iterator;
    }

    map_iterator_proxy& operator++() noexcept {
        ++m_base_iterator;
        return *this;
    }

    map_iterator_proxy operator++(int) & noexcept {
        auto result = *this;
        ++(*this);
        return result;
    }

    bool operator==(const map_iterator_proxy& rhs) const noexcept {
        return m_base_iterator == rhs.m_base_iterator;
    }

    bool operator!=(const map_iterator_proxy& rhs) const noexcept {
        return m_base_iterator != rhs.m_base_iterator;
    }

    typename Iterator::reference key() const {
        return m_base_iterator.key();
    }

    typename Iterator::reference value() const noexcept {
        return m_base_iterator.value();
    }

private:
    Iterator m_base_iterator {};
};

template <typename BasicNodeType>
class map_range_proxy {
    static_assert(
        is_basic_node<BasicNodeType>::value,
        "map_range_proxy only accepts a basic_node type as its template parameter.");

public:
    using iterator = map_iterator_proxy<typename std::conditional<
        std::is_const<BasicNodeType>::value, typename BasicNodeType::const_iterator,
        typename BasicNodeType::iterator>::type>;

    using const_iterator = map_iterator_proxy<typename BasicNodeType::const_iterator>;

    map_range_proxy(BasicNodeType& map) noexcept
        : mp_map(&map) {
    }

    map_range_proxy(const map_range_proxy&) = default;

    map_range_proxy& operator=(const map_range_proxy&) = default;

    map_range_proxy(map_range_proxy&&) = default;

    map_range_proxy& operator=(map_range_proxy&&) = default;

    ~map_range_proxy() = default;

    iterator begin() noexcept {
        return {mp_map->begin()};
    }

    const_iterator begin() const noexcept {
        return {mp_map->cbegin()};
    }

    iterator end() noexcept {
        return {mp_map->end()};
    }

    const_iterator end() const noexcept {
        return {mp_map->cend()};
    }

private:
    BasicNodeType* mp_map {nullptr};
};

FK_YAML_DETAIL_NAMESPACE_END

#endif

#ifndef FK_YAML_DETAIL_NODE_REF_STORAGE_HPP
#define FK_YAML_DETAIL_NODE_REF_STORAGE_HPP

#include <initializer_list>
#include <type_traits>
#include <utility>

FK_YAML_DETAIL_NAMESPACE_BEGIN

template <typename BasicNodeType>
class node_ref_storage {
    static_assert(is_basic_node<BasicNodeType>::value, "node_ref_storage only accepts basic_node<...>");

    using node_type = BasicNodeType;

public:
    explicit node_ref_storage(node_type&& n) noexcept(std::is_nothrow_move_constructible<node_type>::value)
        : m_owned_value(std::move(n)) {
    }

    explicit node_ref_storage(const node_type& n) noexcept
        : m_value_ref(&n) {
    }

    node_ref_storage(std::initializer_list<node_ref_storage> init)
        : m_owned_value(init) {
    }

    template <typename... Args, enable_if_t<std::is_constructible<node_type, Args...>::value, int> = 0>
    node_ref_storage(Args&&... args)
        : m_owned_value(std::forward<Args>(args)...) {
    }

    node_ref_storage(const node_ref_storage&) = delete;
    node_ref_storage(node_ref_storage&&) = default;
    node_ref_storage& operator=(const node_ref_storage&) = delete;
    node_ref_storage& operator=(node_ref_storage&&) = default;

    ~node_ref_storage() = default;

public:
    const node_type* operator->() const noexcept {
        return m_value_ref ? m_value_ref : &m_owned_value;
    }

    node_type release() const noexcept {
        return m_value_ref ? *m_value_ref : std::move(m_owned_value);
    }

private:
    mutable node_type m_owned_value = nullptr;
    const node_type* m_value_ref = nullptr;
};

FK_YAML_DETAIL_NAMESPACE_END

#endif

#ifndef FK_YAML_DETAIL_OUTPUT_SERIALIZER_HPP
#define FK_YAML_DETAIL_OUTPUT_SERIALIZER_HPP

#include <cmath>
#include <sstream>
#include <string>
#include <vector>

#ifndef FK_YAML_DETAIL_CONVERSIONS_TO_STRING_HPP
#define FK_YAML_DETAIL_CONVERSIONS_TO_STRING_HPP

#include <cmath>
#include <limits>
#include <string>
#include <sstream>
#include <type_traits>

FK_YAML_DETAIL_NAMESPACE_BEGIN

template <typename ValueType, typename CharType>
inline void to_string(ValueType v, std::basic_string<CharType>& s) noexcept;

template <>
inline void to_string(std::nullptr_t, std::string& s) noexcept {
    s = "null";
}

template <>
inline void to_string(bool v, std::string& s) noexcept {
    s = v ? "true" : "false";
}

template <typename IntegerType>
inline enable_if_t<is_non_bool_integral<IntegerType>::value> to_string(IntegerType v, std::string& s) noexcept {
    s = std::to_string(v);
}

template <typename FloatType>
inline enable_if_t<std::is_floating_point<FloatType>::value> to_string(FloatType v, std::string& s) noexcept {
    if (std::isnan(v)) {
        s = ".nan";
        return;
    }

    if (std::isinf(v)) {
        if (v == std::numeric_limits<FloatType>::infinity()) {
            s = ".inf";
        }
        else {
            s = "-.inf";
        }
        return;
    }

    std::ostringstream oss;
    oss << v;
    s = oss.str();

    const std::size_t pos = s.find_first_of(".e");
    if (pos == std::string::npos) {
        s += ".0";
    }
}

FK_YAML_DETAIL_NAMESPACE_END

#endif

FK_YAML_DETAIL_NAMESPACE_BEGIN

template <typename BasicNodeType>
class basic_serializer {
    static_assert(detail::is_basic_node<BasicNodeType>::value, "basic_serializer only accepts basic_node<...>");

public:
    basic_serializer() = default;

    std::string serialize(const BasicNodeType& node) {
        std::string str {};
        serialize_document(node, str);
        return str;
    }

    std::string serialize_docs(const std::vector<BasicNodeType>& docs) {
        std::string str {};

        const auto size = static_cast<uint32_t>(docs.size());
        for (uint32_t i = 0; i < size; i++) {
            serialize_document(docs[i], str);
            if (i + 1 < size) {
                str += "...\n";
            }
        }

        return str;
    }

private:
    void serialize_document(const BasicNodeType& node, std::string& str) {
        const bool dirs_serialized = serialize_directives(node, str);

        const bool root_has_props = node.is_anchor() || node.has_tag_name();

        if (root_has_props) {
            if (dirs_serialized) {
                str.back() = ' ';
            }
            bool is_anchor_appended = try_append_anchor(node, false, str);
            try_append_tag(node, is_anchor_appended, str);
            str += "\n";
        }
        serialize_node(node, 0, str);
    }

    bool serialize_directives(const BasicNodeType& node, std::string& str) {
        const auto& p_meta = node.mp_meta;
        bool needs_directive_end = false;

        if (p_meta->is_version_specified) {
            str += "%YAML ";
            switch (p_meta->version) {
            case yaml_version_type::VERSION_1_1:
                str += "1.1\n";
                break;
            case yaml_version_type::VERSION_1_2:
                str += "1.2\n";
                break;
            }
            needs_directive_end = true;
        }

        if (!p_meta->primary_handle_prefix.empty()) {
            str += "%TAG ! ";
            str += p_meta->primary_handle_prefix;
            str += "\n";
            needs_directive_end = true;
        }

        if (!p_meta->secondary_handle_prefix.empty()) {
            str += "%TAG !! ";
            str += p_meta->secondary_handle_prefix;
            str += "\n";
            needs_directive_end = true;
        }

        if (!p_meta->named_handle_map.empty()) {
            for (const auto& itr : p_meta->named_handle_map) {
                str += "%TAG ";
                str += itr.first;
                str += " ";
                str += itr.second;
                str += "\n";
            }
            needs_directive_end = true;
        }

        if (needs_directive_end) {
            str += "---\n";
        }

        return needs_directive_end;
    }

    void serialize_node(const BasicNodeType& node, const uint32_t cur_indent, std::string& str) {
        switch (node.get_type()) {
        case node_type::SEQUENCE:
            if (node.size() == 0) {
                str += "[]\n";
                return;
            }
            for (const auto& seq_item : node) {
                insert_indentation(cur_indent, str);
                str += "-";

                const bool is_appended = try_append_alias(seq_item, true, str);
                if (is_appended) {
                    str += "\n";
                    continue;
                }

                try_append_anchor(seq_item, true, str);
                try_append_tag(seq_item, true, str);

                const bool is_scalar = seq_item.is_scalar();
                if (is_scalar) {
                    str += " ";
                    serialize_node(seq_item, cur_indent, str);
                    str += "\n";
                    continue;
                }

                const bool is_empty = seq_item.empty();
                if (!is_empty) {
                    str += "\n";
                    serialize_node(seq_item, cur_indent + 2, str);
                    continue;
                }

                if (seq_item.is_sequence()) {
                    str += " []\n";
                }
                else {
                    str += " {}\n";
                }
            }
            break;
        case node_type::MAPPING:
            if (node.size() == 0) {
                str += "{}\n";
                return;
            }
            for (auto itr : node.map_items()) {
                insert_indentation(cur_indent, str);

                const auto& key_node = itr.key();

                bool is_appended = try_append_alias(key_node, false, str);
                if (is_appended) {
                    str += " ";
                }
                else {
                    const bool is_anchor_appended = try_append_anchor(key_node, false, str);
                    const bool is_tag_appended = try_append_tag(key_node, is_anchor_appended, str);
                    if (is_anchor_appended || is_tag_appended) {
                        str += " ";
                    }

                    const bool is_container = !key_node.is_scalar();
                    if (is_container) {
                        str += "? ";
                    }
                    const auto indent = static_cast<uint32_t>(get_cur_indent(str));
                    serialize_node(key_node, indent, str);
                    if (is_container) {
                        insert_indentation(indent - 2, str);
                    }
                }

                str += ":";

                const auto& value_node = itr.value();

                is_appended = try_append_alias(value_node, true, str);
                if (is_appended) {
                    str += "\n";
                    continue;
                }

                try_append_anchor(value_node, true, str);
                try_append_tag(value_node, true, str);

                const bool is_scalar = itr->is_scalar();
                if (is_scalar) {
                    str += " ";
                    serialize_node(value_node, cur_indent, str);
                    str += "\n";
                    continue;
                }

                const bool is_empty = itr->empty();
                if (is_empty) {
                    str += " ";
                }
                else {
                    str += "\n";
                }
                serialize_node(value_node, cur_indent + 2, str);
            }
            break;
        case node_type::NULL_OBJECT:
            to_string(nullptr, m_tmp_str_buff);
            str += m_tmp_str_buff;
            break;
        case node_type::BOOLEAN:
            to_string(node.template get_value<typename BasicNodeType::boolean_type>(), m_tmp_str_buff);
            str += m_tmp_str_buff;
            break;
        case node_type::INTEGER:
            to_string(node.template get_value<typename BasicNodeType::integer_type>(), m_tmp_str_buff);
            str += m_tmp_str_buff;
            break;
        case node_type::FLOAT:
            to_string(node.template get_value<typename BasicNodeType::float_number_type>(), m_tmp_str_buff);
            str += m_tmp_str_buff;
            break;
        case node_type::STRING: {
            bool is_escaped = false;
            auto str_val = get_string_node_value(node, is_escaped);

            if (is_escaped) {
                str += '\"';
                str += str_val;
                str += '\"';
                break;
            }

            const node_type type_if_plain =
                scalar_scanner::scan(str_val.c_str(), str_val.c_str() + str_val.size());

            if (type_if_plain != node_type::STRING) {
                str += '\"';
                str += str_val;
                str += '\"';
            }
            else {
                str += str_val;
            }
            break;
        }
        }
    }

    std::size_t get_cur_indent(const std::string& s) const noexcept {
        const bool is_empty = s.empty();
        if (is_empty) {
            return 0;
        }

        const std::size_t last_lf_pos = s.rfind('\n');
        return (last_lf_pos != std::string::npos) ? s.size() - last_lf_pos - 1 : s.size();
    }

    void insert_indentation(const uint32_t indent, std::string& str) const noexcept {
        if (indent == 0) {
            return;
        }

        str.append(indent - get_cur_indent(str), ' ');
    }

    bool try_append_anchor(const BasicNodeType& node, bool prepends_space, std::string& str) const {
        if (node.is_anchor()) {
            if (prepends_space) {
                str += " ";
            }
            str += "&" + node.get_anchor_name();
            return true;
        }
        return false;
    }

    bool try_append_alias(const BasicNodeType& node, bool prepends_space, std::string& str) const {
        if (node.is_alias()) {
            if (prepends_space) {
                str += " ";
            }
            str += "*" + node.get_anchor_name();
            return true;
        }
        return false;
    }

    bool try_append_tag(const BasicNodeType& node, bool prepends_space, std::string& str) const {
        if (node.has_tag_name()) {
            if (prepends_space) {
                str += " ";
            }
            str += node.get_tag_name();
            return true;
        }
        return false;
    }

    typename BasicNodeType::string_type get_string_node_value(const BasicNodeType& node, bool& is_escaped) {
        FK_YAML_ASSERT(node.is_string());

        const auto& s = node.as_str();
        return yaml_escaper::escape(s.c_str(), s.c_str() + s.size(), is_escaped);
    }

private:
    std::string m_tmp_str_buff;
};

FK_YAML_DETAIL_NAMESPACE_END

#endif

#ifndef FK_YAML_DETAIL_REVERSE_ITERATOR_HPP
#define FK_YAML_DETAIL_REVERSE_ITERATOR_HPP

#include <iterator>

FK_YAML_DETAIL_NAMESPACE_BEGIN

template <typename Iterator>
class reverse_iterator {
    static_assert(
        is_basic_node<typename Iterator::value_type>::value,
        "reverse_iterator only accepts a basic_node type as the underlying iterator's value type");

public:
    using iterator_type = Iterator;

    using iterator_category = typename Iterator::iterator_category;

    using value_type = typename Iterator::value_type;

    using difference_type = typename Iterator::difference_type;

    using pointer = typename Iterator::pointer;

    using reference = typename Iterator::reference;

    reverse_iterator() = default;

    reverse_iterator(const reverse_iterator&) = default;

    reverse_iterator& operator=(const reverse_iterator&) = default;

    reverse_iterator(reverse_iterator&&) = default;

    reverse_iterator& operator=(reverse_iterator&&) = default;

    reverse_iterator(const Iterator& i) noexcept
        : m_current(i) {
    }

    template <typename U, enable_if_t<negation<std::is_same<U, Iterator>>::value, int> = 0>
    reverse_iterator(const reverse_iterator<U>& other) noexcept
        : m_current(other.base()) {
    }

    template <typename U, enable_if_t<negation<std::is_same<U, Iterator>>::value, int> = 0>
    reverse_iterator& operator=(const reverse_iterator<U>& other) noexcept {
        m_current = other.base();
        return *this;
    }

    ~reverse_iterator() = default;

    Iterator base() const noexcept {
        return m_current;
    }

    reference operator*() const noexcept {
        Iterator tmp = m_current;
        return *--tmp;
    }

    pointer operator->() const noexcept {
        return &(operator*());
    }

    reverse_iterator& operator++() noexcept {
        --m_current;
        return *this;
    }

    reverse_iterator operator++(int) & noexcept {
        auto result = *this;
        --m_current;
        return result;
    }

    reverse_iterator& operator--() noexcept {
        ++m_current;
        return *this;
    }

    reverse_iterator operator--(int) & noexcept {
        auto result = *this;
        ++m_current;
        return result;
    }

    reverse_iterator operator+(difference_type n) const noexcept {
        return reverse_iterator(m_current - n);
    }

    reverse_iterator& operator+=(difference_type n) noexcept {
        m_current -= n;
        return *this;
    }

    reverse_iterator operator-(difference_type n) const noexcept {
        return reverse_iterator(m_current + n);
    }

    reverse_iterator& operator-=(difference_type n) noexcept {
        m_current += n;
        return *this;
    }

    auto key() const -> decltype(std::declval<Iterator>().key()) {
        Iterator itr = --(base());
        return itr.key();
    }

    reference value() noexcept {
        Iterator itr = --(base());
        return *itr;
    }

private:
    Iterator m_current;
};

template <typename IteratorL, typename IteratorR>
inline bool operator==(const reverse_iterator<IteratorL>& lhs, const reverse_iterator<IteratorR>& rhs) {
    return lhs.base() == rhs.base();
}

template <typename IteratorL, typename IteratorR>
inline bool operator!=(const reverse_iterator<IteratorL>& lhs, const reverse_iterator<IteratorR>& rhs) {
    return lhs.base() != rhs.base();
}

template <typename IteratorL, typename IteratorR>
inline bool operator<(const reverse_iterator<IteratorL>& lhs, const reverse_iterator<IteratorR>& rhs) {
    return lhs.base() > rhs.base();
}

template <typename IteratorL, typename IteratorR>
inline bool operator<=(const reverse_iterator<IteratorL>& lhs, const reverse_iterator<IteratorR>& rhs) {
    return lhs.base() >= rhs.base();
}

template <typename IteratorL, typename IteratorR>
inline bool operator>(const reverse_iterator<IteratorL>& lhs, const reverse_iterator<IteratorR>& rhs) {
    return lhs.base() < rhs.base();
}

template <typename IteratorL, typename IteratorR>
inline bool operator>=(const reverse_iterator<IteratorL>& lhs, const reverse_iterator<IteratorR>& rhs) {
    return lhs.base() <= rhs.base();
}

FK_YAML_DETAIL_NAMESPACE_END

#endif

#ifndef FK_YAML_DETAIL_TYPES_YAML_VERSION_T_HPP
#define FK_YAML_DETAIL_TYPES_YAML_VERSION_T_HPP

#include <cstdint>

FK_YAML_DETAIL_NAMESPACE_BEGIN

enum class yaml_version_t : std::uint8_t {
    VER_1_1,
    VER_1_2,
};

inline yaml_version_t convert_from_yaml_version_type(yaml_version_type t) noexcept {
    switch (t) {
    case yaml_version_type::VERSION_1_1:
        return yaml_version_t::VER_1_1;
    case yaml_version_type::VERSION_1_2:
    default:
        return yaml_version_t::VER_1_2;
    }
}

inline yaml_version_type convert_to_yaml_version_type(yaml_version_t t) noexcept {
    switch (t) {
    case yaml_version_t::VER_1_1:
        return yaml_version_type::VERSION_1_1;
    case yaml_version_t::VER_1_2:
    default:
        return yaml_version_type::VERSION_1_2;
    }
}

FK_YAML_DETAIL_NAMESPACE_END

#endif

#ifndef FK_YAML_NODE_VALUE_CONVERTER_HPP
#define FK_YAML_NODE_VALUE_CONVERTER_HPP

#include <utility>

#ifndef FK_YAML_DETAIL_CONVERSIONS_FROM_NODE_HPP
#define FK_YAML_DETAIL_CONVERSIONS_FROM_NODE_HPP

#include <array>
#include <cmath>
#include <forward_list>
#include <limits>
#include <utility>
#include <valarray>

#ifdef FK_YAML_HAS_CXX_17
#include <optional>
#endif

FK_YAML_DETAIL_NAMESPACE_BEGIN

template <typename T>
using is_constructible_mapping_type =
    conjunction<detect::has_key_type<T>, detect::has_mapped_type<T>, detect::has_value_type<T>>;

template <typename BasicNodeType, typename T>
using is_constructible_sequence_type = conjunction<
    negation<is_basic_node<T>>, detect::has_iterator<T>, detect::is_iterator_traits<typename T::iterator>,
    detect::has_begin_end<T>, negation<std::is_same<T, typename BasicNodeType::mapping_type>>,
    negation<is_constructible_mapping_type<T>>>;

template <typename T>
using is_sequence_container_adapter = conjunction<
    negation<is_basic_node<T>>, detect::has_container_type<T>, detect::has_value_type<T>,
    negation<detect::has_key_type<T>>>;

template <typename ContainerType, typename = void>
struct call_reserve_if_available {
    static void call(ContainerType&, typename ContainerType::size_type) {
    }
};

template <typename ContainerType>
struct call_reserve_if_available<ContainerType, enable_if_t<detect::has_reserve<ContainerType>::value>> {
    static void call(ContainerType& c, typename ContainerType::size_type n) {
        c.reserve(n);
    }
};

template <typename BasicNodeType, typename T, std::size_t N>
inline auto from_node(const BasicNodeType& n, T (&array)[N])
    -> decltype(n.get_value_inplace(std::declval<T&>()), void()) {
    if FK_YAML_UNLIKELY (!n.is_sequence()) {
        throw type_error("The target node value type is not sequence type.", n.get_type());
    }

    for (std::size_t i = 0; i < N; i++) {
        n.at(i).get_value_inplace(array[i]);
    }
}

template <typename BasicNodeType, typename T, std::size_t N0, std::size_t N1>
inline auto from_node(const BasicNodeType& n, T (&array)[N0][N1])
    -> decltype(n.get_value_inplace(std::declval<T&>()), void()) {
    if FK_YAML_UNLIKELY (!n.is_sequence()) {
        throw type_error("The target node value type is not sequence type.", n.get_type());
    }

    for (std::size_t i0 = 0; i0 < N0; i0++) {
        for (std::size_t i1 = 0; i1 < N1; i1++) {
            n.at(i0).at(i1).get_value_inplace(array[i0][i1]);
        }
    }
}

template <typename BasicNodeType, typename T, std::size_t N0, std::size_t N1, std::size_t N2>
inline auto from_node(const BasicNodeType& n, T (&array)[N0][N1][N2])
    -> decltype(n.get_value_inplace(std::declval<T&>()), void()) {
    if FK_YAML_UNLIKELY (!n.is_sequence()) {
        throw type_error("The target node value type is not sequence type.", n.get_type());
    }

    for (std::size_t i0 = 0; i0 < N0; i0++) {
        for (std::size_t i1 = 0; i1 < N1; i1++) {
            for (std::size_t i2 = 0; i2 < N2; i2++) {
                n.at(i0).at(i1).at(i2).get_value_inplace(array[i0][i1][i2]);
            }
        }
    }
}

template <typename BasicNodeType, typename T, std::size_t N>
inline auto from_node(const BasicNodeType& n, std::array<T, N>& arr)
    -> decltype(n.get_value_inplace(std::declval<T&>()), void()) {
    if FK_YAML_UNLIKELY (!n.is_sequence()) {
        throw type_error("The target node value type is not sequence type.", n.get_type());
    }

    for (std::size_t i = 0; i < N; i++) {
        n.at(i).get_value_inplace(arr.at(i));
    }
}

template <typename BasicNodeType, typename T>
inline auto from_node(const BasicNodeType& n, std::valarray<T>& va)
    -> decltype(n.get_value_inplace(std::declval<T&>()), void()) {
    if FK_YAML_UNLIKELY (!n.is_sequence()) {
        throw type_error("The target node value type is not sequence type.", n.get_type());
    }

    std::size_t count = n.size();
    va.resize(count);
    for (std::size_t i = 0; i < count; i++) {
        n.at(i).get_value_inplace(va[i]);
    }
}

template <typename BasicNodeType, typename T, typename Alloc>
inline auto from_node(const BasicNodeType& n, std::forward_list<T, Alloc>& fl)
    -> decltype(n.template get_value<T>(), void()) {
    if FK_YAML_UNLIKELY (!n.is_sequence()) {
        throw type_error("The target node value is not sequence type.", n.get_type());
    }

    fl.clear();

    auto insert_pos_itr = fl.before_begin();
    for (const auto& elem : n) {
        insert_pos_itr = fl.emplace_after(insert_pos_itr, elem.template get_value<T>());
    }
}

template <
    typename BasicNodeType, typename CompatSeqType,
    enable_if_t<
        conjunction<
            is_basic_node<BasicNodeType>, is_constructible_sequence_type<BasicNodeType, CompatSeqType>,
            negation<std::is_constructible<typename BasicNodeType::string_type, CompatSeqType>>>::value,
        int> = 0>
inline auto from_node(const BasicNodeType& n, CompatSeqType& s)
    -> decltype(n.template get_value<typename CompatSeqType::value_type>(), void()) {
    if FK_YAML_UNLIKELY (!n.is_sequence()) {
        throw type_error("The target node value is not sequence type.", n.get_type());
    }

    s.clear();

    call_reserve_if_available<CompatSeqType>::call(s, n.size());

    using std::end;
    std::transform(n.begin(), n.end(), std::inserter(s, end(s)), [](const BasicNodeType& elem) {
        return elem.template get_value<typename CompatSeqType::value_type>();
    });
}

template <
    typename BasicNodeType, typename SeqContainerAdapter,
    enable_if_t<
        conjunction<is_basic_node<BasicNodeType>, is_sequence_container_adapter<SeqContainerAdapter>>::value, int> = 0>
inline auto from_node(const BasicNodeType& n, SeqContainerAdapter& ca)
    -> decltype(n.template get_value<typename SeqContainerAdapter::value_type>(), ca.push(std::declval<typename SeqContainerAdapter::value_type>()), void()) {
    if FK_YAML_UNLIKELY (!n.is_sequence()) {
        throw type_error("The target node value is not sequence type.", n.get_type());
    }

    while (!ca.empty()) {
        ca.pop();
    }

    for (const auto& elem : n) {
        ca.push(elem.template get_value<typename SeqContainerAdapter::value_type>());
    }
}

template <typename BasicNodeType, typename CompatMapType, enable_if_t<is_constructible_mapping_type<CompatMapType>::value, int> = 0>
inline auto from_node(const BasicNodeType& n, CompatMapType& m)
    -> decltype(
        std::declval<const BasicNodeType&>().template get_value<typename CompatMapType::key_type>(),
        std::declval<const BasicNodeType&>().template get_value<typename CompatMapType::mapped_type>(),
        m.emplace(std::declval<typename CompatMapType::key_type>(), std::declval<typename CompatMapType::mapped_type>()),
        void()) {
    if FK_YAML_UNLIKELY (!n.is_mapping()) {
        throw type_error("The target node value type is not mapping type.", n.get_type());
    }

    m.clear();
    call_reserve_if_available<CompatMapType>::call(m, n.size());

    for (const auto& pair : n.as_map()) {
        m.emplace(
            pair.first.template get_value<typename CompatMapType::key_type>(),
            pair.second.template get_value<typename CompatMapType::mapped_type>());
    }
}

template <typename BasicNodeType, enable_if_t<is_basic_node<BasicNodeType>::value, int> = 0>
inline void from_node(const BasicNodeType& n, std::nullptr_t& null) {
    if FK_YAML_UNLIKELY (!n.is_null()) {
        throw type_error("The target node value type is not null type.", n.get_type());
    }
    null = nullptr;
}

template <typename BasicNodeType, enable_if_t<is_basic_node<BasicNodeType>::value, int> = 0>
inline void from_node(const BasicNodeType& n, bool& b) {
    switch (n.get_type()) {
    case node_type::NULL_OBJECT:
        b = false;
        break;
    case node_type::BOOLEAN:
        b = static_cast<bool>(n.as_bool());
        break;
    case node_type::INTEGER:
        b = (n.as_int() != 0);
        break;
    case node_type::FLOAT:
        using float_type = typename BasicNodeType::float_number_type;
        b = (n.as_float() != static_cast<float_type>(0.));
        break;
    case node_type::SEQUENCE:
    case node_type::MAPPING:
    case node_type::STRING:
    default:
        throw type_error("The target node value type is not compatible with boolean type.", n.get_type());
    }
}

template <
    typename BasicNodeType, typename IntType, bool = std::is_same<typename BasicNodeType::integer_type, IntType>::value>
struct from_node_int_helper {
    static IntType convert(const BasicNodeType& n) {
        return n.as_int();
    }
};

template <typename BasicNodeType, typename IntType>
struct from_node_int_helper<BasicNodeType, IntType, false> {
    static IntType convert(const BasicNodeType& n) {
        using node_int_type = typename BasicNodeType::integer_type;
        const node_int_type tmp_int = n.as_int();

        if (std::is_same<IntType, uint64_t>::value) {
            if FK_YAML_UNLIKELY (tmp_int < 0) {
                throw exception("Integer value underflow detected.");
            }
        }
        else {
            if FK_YAML_UNLIKELY (tmp_int < static_cast<node_int_type>(std::numeric_limits<IntType>::min())) {
                throw exception("Integer value underflow detected.");
            }
            if FK_YAML_UNLIKELY (static_cast<node_int_type>(std::numeric_limits<IntType>::max()) < tmp_int) {
                throw exception("Integer value overflow detected.");
            }
        }

        return static_cast<IntType>(tmp_int);
    }
};

template <
    typename BasicNodeType, typename IntegerType,
    enable_if_t<conjunction<is_basic_node<BasicNodeType>, is_non_bool_integral<IntegerType>>::value, int> = 0>
inline void from_node(const BasicNodeType& n, IntegerType& i) {
    switch (n.get_type()) {
    case node_type::NULL_OBJECT:
        i = static_cast<IntegerType>(0);
        break;
    case node_type::BOOLEAN:
        i = static_cast<bool>(n.as_bool()) ? static_cast<IntegerType>(1) : static_cast<IntegerType>(0);
        break;
    case node_type::INTEGER:
        i = from_node_int_helper<BasicNodeType, IntegerType>::convert(n);
        break;
    case node_type::FLOAT: {
        const auto tmp_int = static_cast<int64_t>(n.as_float());

        if (std::is_same<IntegerType, uint64_t>::value) {
            if FK_YAML_UNLIKELY (tmp_int < 0) {
                throw exception("Integer value underflow detected.");
            }
        }
        else {
            if FK_YAML_UNLIKELY (tmp_int < static_cast<int64_t>(std::numeric_limits<IntegerType>::min())) {
                throw exception("Integer value underflow detected.");
            }
            if FK_YAML_UNLIKELY (static_cast<int64_t>(std::numeric_limits<IntegerType>::max()) < tmp_int) {
                throw exception("Integer value overflow detected.");
            }
        }

        i = static_cast<IntegerType>(tmp_int);
        break;
    }
    case node_type::SEQUENCE:
    case node_type::MAPPING:
    case node_type::STRING:
    default:
        throw type_error("The target node value type is not compatible with integer type.", n.get_type());
    }
}

template <
    typename BasicNodeType, typename FloatType,
    bool = std::is_same<typename BasicNodeType::float_number_type, FloatType>::value>
struct from_node_float_helper {
    static FloatType convert(const BasicNodeType& n) {
        return n.as_float();
    }
};

template <typename BasicNodeType, typename FloatType>
struct from_node_float_helper<BasicNodeType, FloatType, false> {
    static FloatType convert(const BasicNodeType& n) {
        using node_float_type = typename BasicNodeType::float_number_type;
        auto tmp_float = n.as_float();

        if (std::isinf(tmp_float)) {
            if (tmp_float == std::numeric_limits<node_float_type>::infinity()) {
                return std::numeric_limits<FloatType>::infinity();
            }

            return static_cast<FloatType>(-1.) * std::numeric_limits<FloatType>::infinity();
        }

        if (std::isnan(tmp_float)) {
            return std::numeric_limits<FloatType>::quiet_NaN();
        }

        if FK_YAML_UNLIKELY (tmp_float < std::numeric_limits<FloatType>::lowest()) {
            throw exception("Floating point value underflow detected.");
        }
        if FK_YAML_UNLIKELY (std::numeric_limits<FloatType>::max() < tmp_float) {
            throw exception("Floating point value overflow detected.");
        }

        return static_cast<FloatType>(tmp_float);
    }
};

template <
    typename BasicNodeType, typename FloatType,
    enable_if_t<conjunction<is_basic_node<BasicNodeType>, std::is_floating_point<FloatType>>::value, int> = 0>
inline void from_node(const BasicNodeType& n, FloatType& f) {
    switch (n.get_type()) {
    case node_type::NULL_OBJECT:
        f = static_cast<FloatType>(0.);
        break;
    case node_type::BOOLEAN:
        f = static_cast<bool>(n.as_bool()) ? static_cast<FloatType>(1.) : static_cast<FloatType>(0.);
        break;
    case node_type::INTEGER:
        f = static_cast<FloatType>(n.as_int());
        break;
    case node_type::FLOAT:
        f = from_node_float_helper<BasicNodeType, FloatType>::convert(n);
        break;
    case node_type::SEQUENCE:
    case node_type::MAPPING:
    case node_type::STRING:
    default:
        throw type_error("The target node value type is not compatible with float number type.", n.get_type());
    }
}

template <typename BasicNodeType, enable_if_t<is_basic_node<BasicNodeType>::value, int> = 0>
inline void from_node(const BasicNodeType& n, typename BasicNodeType::string_type& s) {
    if FK_YAML_UNLIKELY (!n.is_string()) {
        throw type_error("The target node value type is not string type.", n.get_type());
    }
    s = n.as_str();
}

template <
    typename BasicNodeType, typename CompatibleStringType,
    enable_if_t<
        conjunction<
            is_basic_node<BasicNodeType>,
            negation<std::is_same<CompatibleStringType, typename BasicNodeType::string_type>>,
            disjunction<
                std::is_constructible<CompatibleStringType, const typename BasicNodeType::string_type&>,
                std::is_assignable<CompatibleStringType, const typename BasicNodeType::string_type&>>>::value,
        int> = 0>
inline void from_node(const BasicNodeType& n, CompatibleStringType& s) {
    if FK_YAML_UNLIKELY (!n.is_string()) {
        throw type_error("The target node value type is not string type.", n.get_type());
    }
    s = n.as_str();
}

template <typename BasicNodeType, typename T, typename U, enable_if_t<is_basic_node<BasicNodeType>::value, int> = 0>
inline auto from_node(const BasicNodeType& n, std::pair<T, U>& p)
    -> decltype(std::declval<const BasicNodeType&>().template get_value<T>(), std::declval<const BasicNodeType&>().template get_value<U>(), void()) {
    if FK_YAML_UNLIKELY (!n.is_sequence()) {
        throw type_error("The target node value type is not sequence type.", n.get_type());
    }

    n.at(0).get_value_inplace(p.first);
    n.at(1).get_value_inplace(p.second);
}

template <typename BasicNodeType, typename... Types, std::size_t... Idx>
inline std::tuple<Types...> from_node_tuple_impl(const BasicNodeType& n, index_sequence<Idx...>) {
    return std::make_tuple(n.at(Idx).template get_value<Types>()...);
}

template <typename BasicNodeType, typename... Types, enable_if_t<is_basic_node<BasicNodeType>::value, int> = 0>
inline void from_node(const BasicNodeType& n, std::tuple<Types...>& t) {
    if FK_YAML_UNLIKELY (!n.is_sequence()) {
        throw type_error("The target node value type is not sequence type.", n.get_type());
    }

    t = from_node_tuple_impl<BasicNodeType, Types...>(n, index_sequence_for<Types...> {});
}

#ifdef FK_YAML_HAS_CXX_17

template <typename BasicNodeType, typename T, enable_if_t<is_basic_node<BasicNodeType>::value, int> = 0>
inline auto from_node(const BasicNodeType& n, std::optional<T>& o) -> decltype(n.template get_value<T>(), void()) {
    try {
        o.emplace(n.template get_value<T>());
    }
    catch (const std::exception&) {
        o = std::nullopt;
    }
}

#endif

struct from_node_fn {
    template <typename BasicNodeType, typename T>
    auto operator()(const BasicNodeType& n, T&& val) const
        noexcept(noexcept(from_node(n, std::forward<T>(val)))) -> decltype(from_node(n, std::forward<T>(val))) {
        return from_node(n, std::forward<T>(val));
    }
};

FK_YAML_DETAIL_NAMESPACE_END

FK_YAML_NAMESPACE_BEGIN

#ifndef FK_YAML_HAS_CXX_17
namespace
{
#endif

FK_YAML_INLINE_VAR constexpr const auto& from_node = detail::static_const<detail::from_node_fn>::value;

#ifndef FK_YAML_HAS_CXX_17
}
#endif

FK_YAML_NAMESPACE_END

#endif

#ifndef FK_YAML_DETAIL_CONVERSIONS_TO_NODE_HPP
#define FK_YAML_DETAIL_CONVERSIONS_TO_NODE_HPP

#include <utility>

FK_YAML_DETAIL_NAMESPACE_BEGIN

template <typename BasicNodeType>
struct external_node_constructor {
    template <typename... Args>
    static void sequence(BasicNodeType& n, Args&&... args) {
        destroy(n);
        n.m_attrs |= node_attr_bits::seq_bit;
        n.m_value.p_seq = create_object<typename BasicNodeType::sequence_type>(std::forward<Args>(args)...);
    }

    template <typename... Args>
    static void mapping(BasicNodeType& n, Args&&... args) {
        destroy(n);
        n.m_attrs |= node_attr_bits::map_bit;
        n.m_value.p_map = create_object<typename BasicNodeType::mapping_type>(std::forward<Args>(args)...);
    }

    static void null_scalar(BasicNodeType& n, std::nullptr_t) {
        destroy(n);
        n.m_attrs |= node_attr_bits::null_bit;
        n.m_value.p_map = nullptr;
    }

    static void boolean_scalar(BasicNodeType& n, const typename BasicNodeType::boolean_type b) {
        destroy(n);
        n.m_attrs |= node_attr_bits::bool_bit;
        n.m_value.boolean = b;
    }

    static void integer_scalar(BasicNodeType& n, const typename BasicNodeType::integer_type i) {
        destroy(n);
        n.m_attrs |= node_attr_bits::int_bit;
        n.m_value.integer = i;
    }

    static void float_scalar(BasicNodeType& n, const typename BasicNodeType::float_number_type f) {
        destroy(n);
        n.m_attrs |= node_attr_bits::float_bit;
        n.m_value.float_val = f;
    }

    template <typename... Args>
    static void string_scalar(BasicNodeType& n, Args&&... args) {
        destroy(n);
        n.m_attrs |= node_attr_bits::string_bit;
        n.m_value.p_str = create_object<typename BasicNodeType::string_type>(std::forward<Args>(args)...);
    }

private:
    static void destroy(BasicNodeType& n) {
        n.m_value.destroy(n.m_attrs & node_attr_mask::value);
        n.m_attrs &= ~node_attr_mask::value;
    }
};

template <
    typename BasicNodeType, typename T,
    enable_if_t<
        conjunction<
            is_basic_node<BasicNodeType>,
            std::is_same<typename BasicNodeType::sequence_type, remove_cvref_t<T>>>::value,
        int> = 0>
inline void to_node(BasicNodeType& n, T&& s) noexcept {
    external_node_constructor<BasicNodeType>::sequence(n, std::forward<T>(s));
}

template <
    typename BasicNodeType, typename CompatSeqType,
    enable_if_t<
        conjunction<
            is_basic_node<BasicNodeType>,
            negation<std::is_same<typename BasicNodeType::sequence_type, remove_cvref_t<CompatSeqType>>>,
            negation<is_basic_node<CompatSeqType>>, detect::has_begin_end<CompatSeqType>,
            negation<conjunction<detect::has_key_type<CompatSeqType>, detect::has_mapped_type<CompatSeqType>>>,
            negation<std::is_constructible<typename BasicNodeType::string_type, CompatSeqType>>>::value,
        int> = 0>
inline void to_node(BasicNodeType& n, CompatSeqType&& s) {
    using std::begin;
    using std::end;
    external_node_constructor<BasicNodeType>::sequence(n, begin(s), end(s));
}

template <typename BasicNodeType, typename T, typename U>
inline void to_node(BasicNodeType& n, const std::pair<T, U>& p) {
    n = {p.first, p.second};
}

template <typename BasicNodeType, typename... Types, std::size_t... Idx>
inline void to_node_tuple_impl(BasicNodeType& n, const std::tuple<Types...>& t, index_sequence<Idx...>) {
    n = {std::get<Idx>(t)...};
}

template <typename BasicNodeType>
inline void to_node(BasicNodeType& n, const std::tuple<>&) {
    n = BasicNodeType::sequence();
}

template <typename BasicNodeType, typename FirstType, typename... RestTypes>
inline void to_node(BasicNodeType& n, const std::tuple<FirstType, RestTypes...>& t) {
    to_node_tuple_impl(n, t, index_sequence_for<FirstType, RestTypes...> {});
}

template <
    typename BasicNodeType, typename T,
    enable_if_t<
        conjunction<
            is_basic_node<BasicNodeType>, std::is_same<typename BasicNodeType::mapping_type, remove_cvref_t<T>>>::value,
        int> = 0>
inline void to_node(BasicNodeType& n, T&& m) noexcept {
    external_node_constructor<BasicNodeType>::mapping(n, std::forward<T>(m));
}

template <
    typename BasicNodeType, typename CompatMapType,
    enable_if_t<
        conjunction<
            is_basic_node<BasicNodeType>, negation<is_basic_node<CompatMapType>>,
            negation<std::is_same<typename BasicNodeType::mapping_type, remove_cvref_t<CompatMapType>>>,
            detect::has_begin_end<CompatMapType>, detect::has_key_type<CompatMapType>,
            detect::has_mapped_type<CompatMapType>>::value,
        int> = 0>
inline void to_node(BasicNodeType& n, CompatMapType&& m) {
    external_node_constructor<BasicNodeType>::mapping(n);
    auto& map = n.as_map();
    for (const auto& pair : std::forward<CompatMapType>(m)) {
        map.emplace(pair.first, pair.second);
    }
}

template <typename BasicNodeType, enable_if_t<is_basic_node<BasicNodeType>::value, int> = 0>
inline void to_node(BasicNodeType& n, std::nullptr_t) {
    external_node_constructor<BasicNodeType>::null_scalar(n, nullptr);
}

template <typename BasicNodeType, enable_if_t<is_basic_node<BasicNodeType>::value, int> = 0>
inline void to_node(BasicNodeType& n, typename BasicNodeType::boolean_type b) noexcept {
    external_node_constructor<BasicNodeType>::boolean_scalar(n, b);
}

template <
    typename BasicNodeType, typename T,
    enable_if_t<conjunction<is_basic_node<BasicNodeType>, is_non_bool_integral<T>>::value, int> = 0>
inline void to_node(BasicNodeType& n, T i) noexcept {
    external_node_constructor<BasicNodeType>::integer_scalar(n, i);
}

template <
    typename BasicNodeType, typename T,
    enable_if_t<conjunction<is_basic_node<BasicNodeType>, std::is_floating_point<T>>::value, int> = 0>
inline void to_node(BasicNodeType& n, T f) noexcept {
    external_node_constructor<BasicNodeType>::float_scalar(n, f);
}

template <
    typename BasicNodeType, typename T,
    enable_if_t<
        conjunction<
            is_basic_node<BasicNodeType>, negation<is_null_pointer<T>>,
            std::is_constructible<typename BasicNodeType::string_type, T>>::value,
        int> = 0>
inline void to_node(BasicNodeType& n, T&& s) {
    external_node_constructor<BasicNodeType>::string_scalar(n, std::forward<T>(s));
}

struct to_node_fn {
    template <typename BasicNodeType, typename T>
    auto operator()(BasicNodeType& n, T&& val) const
        noexcept(noexcept(to_node(n, std::forward<T>(val)))) -> decltype(to_node(n, std::forward<T>(val))) {
        return to_node(n, std::forward<T>(val));
    }
};

FK_YAML_DETAIL_NAMESPACE_END

FK_YAML_NAMESPACE_BEGIN

#ifndef FK_YAML_HAS_CXX_17
namespace
{
#endif

FK_YAML_INLINE_VAR constexpr const auto& to_node = detail::static_const<detail::to_node_fn>::value;

#ifndef FK_YAML_HAS_CXX_17
}
#endif

FK_YAML_NAMESPACE_END

#endif


FK_YAML_NAMESPACE_BEGIN

template <typename ValueType, typename>
class node_value_converter {
public:
    template <typename BasicNodeType, typename TargetType = ValueType>
    static auto from_node(BasicNodeType&& n, TargetType& val) noexcept(
        noexcept(::fkyaml::from_node(std::forward<BasicNodeType>(n), val)))
        -> decltype(::fkyaml::from_node(std::forward<BasicNodeType>(n), val), void()) {
        ::fkyaml::from_node(std::forward<BasicNodeType>(n), val);
    }

    template <typename BasicNodeType, typename TargetType = ValueType>
    static auto to_node(BasicNodeType& n, TargetType&& val) noexcept(noexcept(::fkyaml::to_node(
        n, std::forward<TargetType>(val)))) -> decltype(::fkyaml::to_node(n, std::forward<TargetType>(val))) {
        ::fkyaml::to_node(n, std::forward<TargetType>(val));
    }
};

FK_YAML_NAMESPACE_END

#endif

#ifndef FK_YAML_ORDERED_MAP_HPP
#define FK_YAML_ORDERED_MAP_HPP

#include <functional>
#include <initializer_list>
#include <memory>
#include <utility>
#include <vector>

FK_YAML_NAMESPACE_BEGIN

template <
    typename Key, typename Value, typename IgnoredCompare = std::less<Key>,
    typename Allocator = std::allocator<std::pair<const Key, Value>>>
class ordered_map : public std::vector<std::pair<const Key, Value>, Allocator> {
public:
    using key_type = Key;

    using mapped_type = Value;

    using Container = std::vector<std::pair<const Key, Value>, Allocator>;

    using value_type = typename Container::value_type;

    using iterator = typename Container::iterator;

    using const_iterator = typename Container::const_iterator;

    using size_type = typename Container::size_type;

    using key_compare = std::equal_to<Key>;

public:
    ordered_map() noexcept(noexcept(Container()))
        : Container() {
    }

    ordered_map(std::initializer_list<value_type> init)
        : Container {init} {
    }

public:
    template <
        typename KeyType,
        detail::enable_if_t<detail::is_usable_as_key_type<key_compare, key_type, KeyType>::value, int> = 0>
    mapped_type& operator[](KeyType&& key) noexcept {
        return emplace(std::forward<KeyType>(key), mapped_type()).first->second;
    }

public:
    template <
        typename KeyType,
        detail::enable_if_t<detail::is_usable_as_key_type<key_compare, key_type, KeyType>::value, int> = 0>
    std::pair<iterator, bool> emplace(KeyType&& key, const mapped_type& value) noexcept {
        for (auto itr = this->begin(); itr != this->end(); ++itr) {
            if (m_compare(itr->first, key)) {
                return {itr, false};
            }
        }
        this->emplace_back(std::forward<KeyType>(key), value);
        return {std::prev(this->end()), true};
    }

    template <
        typename KeyType,
        detail::enable_if_t<detail::is_usable_as_key_type<key_compare, key_type, KeyType>::value, int> = 0>
    mapped_type& at(KeyType&& key) {
        for (auto itr = this->begin(); itr != this->end(); ++itr) {
            if (m_compare(itr->first, key)) {
                return itr->second;
            }
        }
        throw fkyaml::exception("key not found.");
    }

    template <
        typename KeyType,
        detail::enable_if_t<detail::is_usable_as_key_type<key_compare, key_type, KeyType>::value, int> = 0>
    const mapped_type& at(KeyType&& key) const {
        for (auto itr = this->begin(); itr != this->end(); ++itr) {
            if (m_compare(itr->first, key)) {
                return itr->second;
            }
        }
        throw fkyaml::exception("key not found.");
    }

    template <
        typename KeyType,
        detail::enable_if_t<detail::is_usable_as_key_type<key_compare, key_type, KeyType>::value, int> = 0>
    iterator find(KeyType&& key) noexcept {
        for (auto itr = this->begin(); itr != this->end(); ++itr) {
            if (m_compare(itr->first, key)) {
                return itr;
            }
        }
        return this->end();
    }

    template <
        typename KeyType,
        detail::enable_if_t<detail::is_usable_as_key_type<key_compare, key_type, KeyType>::value, int> = 0>
    const_iterator find(KeyType&& key) const noexcept {
        for (auto itr = this->begin(); itr != this->end(); ++itr) {
            if (m_compare(itr->first, key)) {
                return itr;
            }
        }
        return this->end();
    }

private:
    key_compare m_compare {};
};

FK_YAML_NAMESPACE_END

#endif


FK_YAML_NAMESPACE_BEGIN

template <
    template <typename, typename...> class SequenceType, template <typename, typename, typename...> class MappingType,
    typename BooleanType, typename IntegerType, typename FloatNumberType, typename StringType,
    template <typename, typename = void> class ConverterType>
class basic_node {
public:
    using sequence_type = SequenceType<basic_node, std::allocator<basic_node>>;

    using mapping_type = MappingType<basic_node, basic_node>;

    using boolean_type = BooleanType;

    using integer_type = IntegerType;

    using float_number_type = FloatNumberType;

    using string_type = StringType;

    using value_type = basic_node;

    using reference = value_type&;

    using const_reference = const value_type&;

    using pointer = value_type*;

    using const_pointer = const value_type*;

    using size_type = std::size_t;

    using difference_type = std::ptrdiff_t;

    using iterator = fkyaml::detail::iterator<basic_node>;

    using const_iterator = fkyaml::detail::iterator<const basic_node>;

    using reverse_iterator = fkyaml::detail::reverse_iterator<iterator>;

    using const_reverse_iterator = fkyaml::detail::reverse_iterator<const_iterator>;

    template <typename T, typename SFINAE>
    using value_converter_type = ConverterType<T, SFINAE>;

    using node_t = detail::node_t;

    using yaml_version_t = detail::yaml_version_t;

    using map_range = fkyaml::detail::map_range_proxy<basic_node>;

    using const_map_range = fkyaml::detail::map_range_proxy<const basic_node>;

private:
    template <typename BasicNodeType>
    friend struct fkyaml::detail::external_node_constructor;

    template <typename BasicNodeType>
    friend class fkyaml::detail::basic_deserializer;

    template <typename BasicNodeType>
    friend class fkyaml::detail::basic_serializer;

    using deserializer_type = detail::basic_deserializer<basic_node>;
    using serializer_type = detail::basic_serializer<basic_node>;
    using initializer_list_t = std::initializer_list<detail::node_ref_storage<basic_node>>;

    union node_value {
        node_value() = default;

        explicit node_value(detail::node_attr_t value_type_bit) {
            switch (value_type_bit) {
            case detail::node_attr_bits::seq_bit:
                p_seq = detail::create_object<sequence_type>();
                break;
            case detail::node_attr_bits::map_bit:
                p_map = detail::create_object<mapping_type>();
                break;
            case detail::node_attr_bits::null_bit:
                p_map = nullptr;
                break;
            case detail::node_attr_bits::bool_bit:
                boolean = static_cast<boolean_type>(false);
                break;
            case detail::node_attr_bits::int_bit:
                integer = static_cast<integer_type>(0);
                break;
            case detail::node_attr_bits::float_bit:
                float_val = static_cast<float_number_type>(0.0);
                break;
            case detail::node_attr_bits::string_bit:
                p_str = detail::create_object<string_type>();
                break;
            default:
                detail::unreachable();
            }
        }

        void destroy(detail::node_attr_t value_type_bit) {
            switch (value_type_bit) {
            case detail::node_attr_bits::seq_bit:
                p_seq->clear();
                detail::destroy_object<sequence_type>(p_seq);
                p_seq = nullptr;
                break;
            case detail::node_attr_bits::map_bit:
                p_map->clear();
                detail::destroy_object<mapping_type>(p_map);
                p_map = nullptr;
                break;
            case detail::node_attr_bits::string_bit:
                detail::destroy_object<string_type>(p_str);
                p_str = nullptr;
                break;
            default:
                break;
            }
        }

        sequence_type* p_seq;
        mapping_type* p_map {nullptr};
        boolean_type boolean;
        integer_type integer;
        float_number_type float_val;
        string_type* p_str;
    };

public:
    basic_node() = default;

    FK_YAML_DEPRECATED("Since 0.3.12; Use explicit basic_node(const node_type)")
    explicit basic_node(const node_t type)
        : basic_node(detail::convert_to_node_type(type)) {
    }

    explicit basic_node(const node_type type)
        : m_attrs(detail::node_attr_bits::from_node_type(type)),
          m_value(m_attrs & detail::node_attr_mask::value) {
    }

    basic_node(const basic_node& rhs)
        : m_attrs(rhs.m_attrs),
          mp_meta(rhs.mp_meta),
          m_prop(rhs.m_prop) {
        if FK_YAML_LIKELY (!has_anchor_name()) {
            switch (m_attrs & detail::node_attr_mask::value) {
            case detail::node_attr_bits::seq_bit:
                m_value.p_seq = detail::create_object<sequence_type>(*(rhs.m_value.p_seq));
                break;
            case detail::node_attr_bits::map_bit:
                m_value.p_map = detail::create_object<mapping_type>(*(rhs.m_value.p_map));
                break;
            case detail::node_attr_bits::null_bit:
                m_value.p_map = nullptr;
                break;
            case detail::node_attr_bits::bool_bit:
                m_value.boolean = rhs.m_value.boolean;
                break;
            case detail::node_attr_bits::int_bit:
                m_value.integer = rhs.m_value.integer;
                break;
            case detail::node_attr_bits::float_bit:
                m_value.float_val = rhs.m_value.float_val;
                break;
            case detail::node_attr_bits::string_bit:
                m_value.p_str = detail::create_object<string_type>(*(rhs.m_value.p_str));
                break;
            default:
                detail::unreachable();
            }
        }
    }

    basic_node(basic_node&& rhs) noexcept
        : m_attrs(rhs.m_attrs),
          mp_meta(std::move(rhs.mp_meta)),
          m_prop(std::move(rhs.m_prop)) {
        if FK_YAML_LIKELY (!has_anchor_name()) {
            switch (m_attrs & detail::node_attr_mask::value) {
            case detail::node_attr_bits::seq_bit:
                FK_YAML_ASSERT(rhs.m_value.p_seq != nullptr);
                m_value.p_seq = rhs.m_value.p_seq;
                rhs.m_value.p_seq = nullptr;
                break;
            case detail::node_attr_bits::map_bit:
                FK_YAML_ASSERT(rhs.m_value.p_map != nullptr);
                m_value.p_map = rhs.m_value.p_map;
                rhs.m_value.p_map = nullptr;
                break;
            case detail::node_attr_bits::null_bit:
                FK_YAML_ASSERT(rhs.m_value.p_map == nullptr);
                m_value.p_map = rhs.m_value.p_map;
                break;
            case detail::node_attr_bits::bool_bit:
                m_value.boolean = rhs.m_value.boolean;
                rhs.m_value.boolean = static_cast<boolean_type>(false);
                break;
            case detail::node_attr_bits::int_bit:
                m_value.integer = rhs.m_value.integer;
                rhs.m_value.integer = static_cast<integer_type>(0);
                break;
            case detail::node_attr_bits::float_bit:
                m_value.float_val = rhs.m_value.float_val;
                rhs.m_value.float_val = static_cast<float_number_type>(0.0);
                break;
            case detail::node_attr_bits::string_bit:
                FK_YAML_ASSERT(rhs.m_value.p_str != nullptr);
                m_value.p_str = rhs.m_value.p_str;
                rhs.m_value.p_str = nullptr;
                break;
            default:
                detail::unreachable();
            }
        }

        rhs.m_attrs = detail::node_attr_bits::default_bits;
        rhs.m_value.p_map = nullptr;
    }

    template <
        typename CompatibleType, typename U = detail::remove_cvref_t<CompatibleType>,
        detail::enable_if_t<
            detail::conjunction<
                detail::negation<detail::is_basic_node<U>>,
                detail::disjunction<detail::is_node_compatible_type<basic_node, U>>>::value,
            int> = 0>
    basic_node(CompatibleType&& val) noexcept(
        noexcept(ConverterType<U, void>::to_node(std::declval<basic_node&>(), std::declval<CompatibleType>()))) {
        ConverterType<U, void>::to_node(*this, std::forward<CompatibleType>(val));
    }

    template <
        typename NodeRefStorageType,
        detail::enable_if_t<detail::is_node_ref_storage<NodeRefStorageType>::value, int> = 0>
    basic_node(const NodeRefStorageType& node_ref_storage) noexcept
        : basic_node(node_ref_storage.release()) {
    }

    basic_node(initializer_list_t init) {
        bool is_mapping =
            std::all_of(init.begin(), init.end(), [](const detail::node_ref_storage<basic_node>& node_ref) {
                return node_ref->is_sequence() && node_ref->size() == 2;
            });

        if (is_mapping) {
            m_attrs = detail::node_attr_bits::map_bit;
            m_value.p_map = detail::create_object<mapping_type>();

            auto& map = *m_value.p_map;
            for (auto& elem_ref : init) {
                auto elem = elem_ref.release();
                auto& seq = *elem.m_value.p_seq;
                map.emplace(std::move(seq[0]), std::move(seq[1]));
            }
        }
        else {
            m_attrs = detail::node_attr_bits::seq_bit;
            m_value.p_seq = detail::create_object<sequence_type>();

            auto& seq = *m_value.p_seq;
            seq.reserve(std::distance(init.begin(), init.end()));
            for (auto& elem_ref : init) {
                seq.emplace_back(std::move(elem_ref.release()));
            }
        }
    }

    ~basic_node() noexcept
    {
        if (m_attrs & detail::node_attr_mask::anchoring) {
            if (m_attrs & detail::node_attr_bits::anchor_bit) {
                auto itr = mp_meta->anchor_table.equal_range(m_prop.anchor).first;
                std::advance(itr, detail::node_attr_bits::get_anchor_offset(m_attrs));
                itr->second.m_value.destroy(itr->second.m_attrs & detail::node_attr_mask::value);
                itr->second.m_attrs = detail::node_attr_bits::default_bits;
                itr->second.mp_meta.reset();
            }
        }
        else if ((m_attrs & detail::node_attr_bits::null_bit) == 0) {
            m_value.destroy(m_attrs & detail::node_attr_mask::value);
        }

        m_attrs = detail::node_attr_bits::default_bits;
        mp_meta.reset();
    }

public:
    template <typename InputType>
    static basic_node deserialize(InputType&& input) {
        return deserializer_type().deserialize(detail::input_adapter(std::forward<InputType>(input)));
    }

    template <typename ItrType>
    static basic_node deserialize(ItrType begin, ItrType end) {
        return deserializer_type().deserialize(
            detail::input_adapter(std::forward<ItrType>(begin), std::forward<ItrType>(end)));
    }

    template <typename InputType>
    static std::vector<basic_node> deserialize_docs(InputType&& input) {
        return deserializer_type().deserialize_docs(detail::input_adapter(std::forward<InputType>(input)));
    }

    template <typename ItrType>
    static std::vector<basic_node> deserialize_docs(ItrType&& begin, ItrType&& end) {
        return deserializer_type().deserialize_docs(
            detail::input_adapter(std::forward<ItrType>(begin), std::forward<ItrType>(end)));
    }

    static std::string serialize(const basic_node& node) {
        return serializer_type().serialize(node);
    }

    static std::string serialize_docs(const std::vector<basic_node>& docs) {
        return serializer_type().serialize_docs(docs);
    }

    static basic_node sequence() {
        basic_node node;
        node.m_attrs = detail::node_attr_bits::seq_bit;
        node.m_value.p_seq = detail::create_object<sequence_type>();
        return node;
    }

    static basic_node sequence(const sequence_type& seq) {
        basic_node node;
        node.m_attrs = detail::node_attr_bits::seq_bit;
        node.m_value.p_seq = detail::create_object<sequence_type>(seq);
        return node;
    }

    static basic_node sequence(sequence_type&& seq) {
        basic_node node;
        node.m_attrs = detail::node_attr_bits::seq_bit;
        node.m_value.p_seq = detail::create_object<sequence_type>(std::move(seq));
        return node;
    }

    static basic_node mapping() {
        basic_node node;
        node.m_attrs = detail::node_attr_bits::map_bit;
        node.m_value.p_map = detail::create_object<mapping_type>();
        return node;
    }

    static basic_node mapping(const mapping_type& map) {
        basic_node node;
        node.m_attrs = detail::node_attr_bits::map_bit;
        node.m_value.p_map = detail::create_object<mapping_type>(map);
        return node;
    }

    static basic_node mapping(mapping_type&& map) {
        basic_node node;
        node.m_attrs = detail::node_attr_bits::map_bit;
        node.m_value.p_map = detail::create_object<mapping_type>(std::move(map));
        return node;
    }

    static basic_node alias_of(const basic_node& anchor_node) {
        constexpr detail::node_attr_t anchor_bit = detail::node_attr_bits::anchor_bit;

        if FK_YAML_UNLIKELY (!anchor_node.has_anchor_name() || !(anchor_node.m_attrs & anchor_bit)) {
            throw fkyaml::exception("Cannot create an alias without anchor name.");
        }

        basic_node node = anchor_node;
        node.m_attrs &= ~detail::node_attr_mask::anchoring;
        node.m_attrs |= detail::node_attr_bits::alias_bit;
        return node;
    }

public:
    basic_node& operator=(const basic_node& rhs) noexcept {
        basic_node(rhs).swap(*this);
        return *this;
    }

    basic_node& operator=(basic_node&& rhs) noexcept {
        basic_node(std::move(rhs)).swap(*this);
        return *this;
    }

    template <
        typename KeyType, detail::enable_if_t<
                              detail::conjunction<
                                  detail::negation<detail::is_basic_node<KeyType>>,
                                  detail::is_node_compatible_type<basic_node, KeyType>>::value,
                              int> = 0>
    basic_node& operator[](KeyType&& key) {
        basic_node& act_node = resolve_reference();

        if FK_YAML_UNLIKELY (act_node.is_scalar_impl()) {
            throw fkyaml::type_error("operator[] is unavailable for a scalar node.", get_type());
        }

        basic_node key_node = std::forward<KeyType>(key);

        if (act_node.is_sequence_impl()) {
            if FK_YAML_UNLIKELY (!key_node.is_integer()) {
                throw fkyaml::type_error(
                    "An argument of operator[] for sequence nodes must be an integer.", get_type());
            }
            FK_YAML_ASSERT(act_node.m_value.p_seq != nullptr);
            return act_node.m_value.p_seq->operator[](key_node.get_value<int>());
        }

        FK_YAML_ASSERT(act_node.m_value.p_map != nullptr);
        return act_node.m_value.p_map->operator[](std::move(key_node));
    }

    template <
        typename KeyType, detail::enable_if_t<
                              detail::conjunction<
                                  detail::negation<detail::is_basic_node<KeyType>>,
                                  detail::is_node_compatible_type<basic_node, KeyType>>::value,
                              int> = 0>
    const basic_node& operator[](KeyType&& key) const {
        const basic_node& act_node = resolve_reference();

        if FK_YAML_UNLIKELY (act_node.is_scalar_impl()) {
            throw fkyaml::type_error("operator[] is unavailable for a scalar node.", get_type());
        }

        basic_node key_node = std::forward<KeyType>(key);

        if (act_node.is_sequence_impl()) {
            if FK_YAML_UNLIKELY (!key_node.is_integer_impl()) {
                throw fkyaml::type_error(
                    "An argument of operator[] for sequence nodes must be an integer.", get_type());
            }
            FK_YAML_ASSERT(act_node.m_value.p_seq != nullptr);
            return act_node.m_value.p_seq->operator[](key_node.get_value<int>());
        }

        FK_YAML_ASSERT(act_node.m_value.p_map != nullptr);
        return act_node.m_value.p_map->operator[](std::move(key_node));
    }

    template <typename KeyType, detail::enable_if_t<detail::is_basic_node<KeyType>::value, int> = 0>
    basic_node& operator[](KeyType&& key) {
        if FK_YAML_UNLIKELY (is_scalar()) {
            throw fkyaml::type_error("operator[] is unavailable for a scalar node.", get_type());
        }

        const node_value& node_value = resolve_reference().m_value;

        if (is_sequence()) {
            if FK_YAML_UNLIKELY (!key.is_integer()) {
                throw fkyaml::type_error(
                    "An argument of operator[] for sequence nodes must be an integer.", get_type());
            }
            FK_YAML_ASSERT(node_value.p_seq != nullptr);
            return node_value.p_seq->operator[](std::forward<KeyType>(key).template get_value<int>());
        }

        FK_YAML_ASSERT(node_value.p_map != nullptr);
        return node_value.p_map->operator[](std::forward<KeyType>(key));
    }

    template <typename KeyType, detail::enable_if_t<detail::is_basic_node<KeyType>::value, int> = 0>
    const basic_node& operator[](KeyType&& key) const {
        if FK_YAML_UNLIKELY (is_scalar()) {
            throw fkyaml::type_error("operator[] is unavailable for a scalar node.", get_type());
        }

        const node_value& node_value = resolve_reference().m_value;

        if (is_sequence()) {
            if FK_YAML_UNLIKELY (!key.is_integer()) {
                throw fkyaml::type_error(
                    "An argument of operator[] for sequence nodes must be an integer.", get_type());
            }
            FK_YAML_ASSERT(node_value.p_seq != nullptr);
            return node_value.p_seq->operator[](key.template get_value<int>());
        }

        FK_YAML_ASSERT(node_value.p_map != nullptr);
        return node_value.p_map->operator[](std::forward<KeyType>(key));
    }

    bool operator==(const basic_node& rhs) const noexcept {
        const basic_node& lhs = resolve_reference();
        const basic_node& act_rhs = rhs.resolve_reference();

        const detail::node_attr_t lhs_val_bit = lhs.m_attrs & detail::node_attr_mask::value;
        if (lhs_val_bit != (act_rhs.m_attrs & detail::node_attr_mask::value)) {
            return false;
        }

        bool ret = false;
        switch (lhs_val_bit) {
        case detail::node_attr_bits::seq_bit:
            ret = (*(lhs.m_value.p_seq) == *(act_rhs.m_value.p_seq));
            break;
        case detail::node_attr_bits::map_bit:
            ret = (*(lhs.m_value.p_map) == *(act_rhs.m_value.p_map));
            break;
        case detail::node_attr_bits::null_bit:
            ret = true;
            break;
        case detail::node_attr_bits::bool_bit:
            ret = (lhs.m_value.boolean == act_rhs.m_value.boolean);
            break;
        case detail::node_attr_bits::int_bit:
            ret = (lhs.m_value.integer == act_rhs.m_value.integer);
            break;
        case detail::node_attr_bits::float_bit:
            ret =
                (std::abs(lhs.m_value.float_val - act_rhs.m_value.float_val) <
                 std::numeric_limits<float_number_type>::epsilon());
            break;
        case detail::node_attr_bits::string_bit:
            ret = (*(lhs.m_value.p_str) == *(act_rhs.m_value.p_str));
            break;
        default:
            detail::unreachable();
        }

        return ret;
    }

    bool operator!=(const basic_node& rhs) const noexcept {
        return !operator==(rhs);
    }

    bool operator<(const basic_node& rhs) const noexcept {
        if (operator==(rhs)) {
            return false;
        }

        const basic_node& lhs = resolve_reference();
        const basic_node& act_rhs = rhs.resolve_reference();

        const detail::node_attr_t lhs_val_bit = lhs.m_attrs & detail::node_attr_mask::value;
        const detail::node_attr_t rhs_val_bit = act_rhs.m_attrs & detail::node_attr_mask::value;

        if (lhs_val_bit < rhs_val_bit) {
            return true;
        }

        if (lhs_val_bit != rhs_val_bit) {
            return false;
        }

        bool ret = false;
        switch (lhs_val_bit) {
        case detail::node_attr_bits::seq_bit:
            ret = (*(lhs.m_value.p_seq) < *(act_rhs.m_value.p_seq));
            break;
        case detail::node_attr_bits::map_bit:
            ret = (*(lhs.m_value.p_map) < *(act_rhs.m_value.p_map));
            break;
        case detail::node_attr_bits::null_bit:
            detail::unreachable();
        case detail::node_attr_bits::bool_bit:
            ret = (!lhs.m_value.boolean && act_rhs.m_value.boolean);
            break;
        case detail::node_attr_bits::int_bit:
            ret = (lhs.m_value.integer < act_rhs.m_value.integer);
            break;
        case detail::node_attr_bits::float_bit:
            ret = (lhs.m_value.float_val < act_rhs.m_value.float_val);
            break;
        case detail::node_attr_bits::string_bit:
            ret = (*(lhs.m_value.p_str) < *(act_rhs.m_value.p_str));
            break;
        default:
            detail::unreachable();
        }

        return ret;
    }

    bool operator<=(const basic_node& rhs) const noexcept {
        return !rhs.operator<(*this);
    }

    bool operator>(const basic_node& rhs) const noexcept {
        return !operator<=(rhs);
    }

    bool operator>=(const basic_node& rhs) const noexcept {
        return !operator<(rhs);
    }

public:
    node_type get_type() const noexcept {
        return detail::node_attr_bits::to_node_type(resolve_reference().m_attrs);
    }

    FK_YAML_DEPRECATED("Since 0.3.12; Use get_type()")
    node_t type() const noexcept {
        node_type tmp_type = get_type();
        return detail::convert_from_node_type(tmp_type);
    }

    bool is_sequence() const noexcept {
        return resolve_reference().is_sequence_impl();
    }

    bool is_mapping() const noexcept {
        return resolve_reference().is_mapping_impl();
    }

    bool is_null() const noexcept {
        return resolve_reference().is_null_impl();
    }

    bool is_boolean() const noexcept {
        return resolve_reference().is_boolean_impl();
    }

    bool is_integer() const noexcept {
        return resolve_reference().is_integer_impl();
    }

    bool is_float_number() const noexcept {
        return resolve_reference().is_float_number_impl();
    }

    bool is_string() const noexcept {
        return resolve_reference().is_string_impl();
    }

    bool is_scalar() const noexcept {
        return resolve_reference().is_scalar_impl();
    }

    bool is_anchor() const noexcept {
        return m_attrs & detail::node_attr_bits::anchor_bit;
    }

    bool is_alias() const noexcept {
        return m_attrs & detail::node_attr_bits::alias_bit;
    }

    bool empty() const {
        const basic_node& act_node = resolve_reference();
        switch (act_node.m_attrs & detail::node_attr_mask::value) {
        case detail::node_attr_bits::seq_bit: {
            FK_YAML_ASSERT(act_node.m_value.p_seq != nullptr);
            return act_node.m_value.p_seq->empty();
        }
        case detail::node_attr_bits::map_bit: {
            FK_YAML_ASSERT(act_node.m_value.p_map != nullptr);
            return act_node.m_value.p_map->empty();
        }
        case detail::node_attr_bits::string_bit: {
            FK_YAML_ASSERT(act_node.m_value.p_str != nullptr);
            return act_node.m_value.p_str->empty();
        }
        default:
            throw fkyaml::type_error("The target node is not of a container type.", get_type());
        }
    }

    std::size_t size() const {
        const basic_node& act_node = resolve_reference();
        switch (act_node.m_attrs & detail::node_attr_mask::value) {
        case detail::node_attr_bits::seq_bit:
            FK_YAML_ASSERT(act_node.m_value.p_seq != nullptr);
            return act_node.m_value.p_seq->size();
        case detail::node_attr_bits::map_bit:
            FK_YAML_ASSERT(act_node.m_value.p_map != nullptr);
            return act_node.m_value.p_map->size();
        case detail::node_attr_bits::string_bit:
            FK_YAML_ASSERT(act_node.m_value.p_str != nullptr);
            return act_node.m_value.p_str->size();
        default:
            throw fkyaml::type_error("The target node is not of a container type.", get_type());
        }
    }

    template <
        typename KeyType, detail::enable_if_t<
                              detail::disjunction<
                                  detail::is_basic_node<KeyType>,
                                  detail::is_node_compatible_type<basic_node, detail::remove_cvref_t<KeyType>>>::value,
                              int> = 0>
    bool contains(KeyType&& key) const {
        const basic_node& act_node = resolve_reference();
        if FK_YAML_LIKELY (act_node.m_attrs & detail::node_attr_bits::map_bit) {
            FK_YAML_ASSERT(act_node.m_value.p_map != nullptr);
            const auto& map = *act_node.m_value.p_map;
            return map.find(std::forward<KeyType>(key)) != map.end();
        }

        return false;
    }

    template <
        typename KeyType, detail::enable_if_t<
                              detail::conjunction<
                                  detail::negation<detail::is_basic_node<KeyType>>,
                                  detail::is_node_compatible_type<basic_node, KeyType>>::value,
                              int> = 0>
    basic_node& at(KeyType&& key) {
        basic_node& act_node = resolve_reference();

        if FK_YAML_UNLIKELY (act_node.is_scalar_impl()) {
            throw fkyaml::type_error("at() is unavailable for a scalar node.", get_type());
        }

        basic_node node_key = std::forward<KeyType>(key);

        if (act_node.is_sequence_impl()) {
            if FK_YAML_UNLIKELY (!node_key.is_integer_impl()) {
                throw fkyaml::type_error("An argument of at() for sequence nodes must be an integer.", get_type());
            }

            FK_YAML_ASSERT(act_node.m_value.p_seq != nullptr);
            sequence_type& seq = *act_node.m_value.p_seq;
            int index = std::move(node_key).template get_value<int>();
            int size = static_cast<int>(seq.size());
            if FK_YAML_UNLIKELY (index >= size) {
                throw fkyaml::out_of_range(index);
            }
            return seq[index];
        }

        FK_YAML_ASSERT(act_node.m_value.p_map != nullptr);
        mapping_type& map = *act_node.m_value.p_map;
        const bool is_found = map.find(node_key) != map.end();
        if FK_YAML_UNLIKELY (!is_found) {
            throw fkyaml::out_of_range(serialize(node_key).c_str());
        }
        return map[std::move(node_key)];
    }

    template <
        typename KeyType, detail::enable_if_t<
                              detail::conjunction<
                                  detail::negation<detail::is_basic_node<KeyType>>,
                                  detail::is_node_compatible_type<basic_node, KeyType>>::value,
                              int> = 0>
    const basic_node& at(KeyType&& key) const {
        const basic_node& act_node = resolve_reference();

        if FK_YAML_UNLIKELY (act_node.is_scalar_impl()) {
            throw fkyaml::type_error("at() is unavailable for a scalar node.", get_type());
        }

        basic_node node_key = std::forward<KeyType>(key);

        if (act_node.is_sequence_impl()) {
            if FK_YAML_UNLIKELY (!node_key.is_integer()) {
                throw fkyaml::type_error("An argument of at() for sequence nodes must be an integer.", get_type());
            }

            FK_YAML_ASSERT(act_node.m_value.p_seq != nullptr);
            const sequence_type& seq = *act_node.m_value.p_seq;
            int index = std::move(node_key).template get_value<int>();
            int size = static_cast<int>(seq.size());
            if FK_YAML_UNLIKELY (index >= size) {
                throw fkyaml::out_of_range(index);
            }
            return seq[index];
        }

        FK_YAML_ASSERT(act_node.m_value.p_map != nullptr);
        const mapping_type& map = *act_node.m_value.p_map;
        const bool is_found = map.find(node_key) != map.end();
        if FK_YAML_UNLIKELY (!is_found) {
            throw fkyaml::out_of_range(serialize(node_key).c_str());
        }
        return map.at(std::move(node_key));
    }

    template <typename KeyType, detail::enable_if_t<detail::is_basic_node<KeyType>::value, int> = 0>
    basic_node& at(KeyType&& key) {
        basic_node& act_node = resolve_reference();
        if FK_YAML_UNLIKELY (act_node.is_scalar_impl()) {
            throw fkyaml::type_error("at() is unavailable for a scalar node.", get_type());
        }

        if (act_node.is_sequence_impl()) {
            if FK_YAML_UNLIKELY (!key.is_integer()) {
                throw fkyaml::type_error("An argument of at() for sequence nodes must be an integer.", get_type());
            }

            FK_YAML_ASSERT(act_node.m_value.p_seq != nullptr);
            sequence_type& seq = *act_node.m_value.p_seq;
            int index = std::forward<KeyType>(key).template get_value<int>();
            int size = static_cast<int>(seq.size());
            if FK_YAML_UNLIKELY (index >= size) {
                throw fkyaml::out_of_range(index);
            }
            return seq[index];
        }

        FK_YAML_ASSERT(act_node.m_value.p_map != nullptr);
        mapping_type& map = *act_node.m_value.p_map;
        bool is_found = map.find(key) != map.end();
        if FK_YAML_UNLIKELY (!is_found) {
            throw fkyaml::out_of_range(serialize(key).c_str());
        }
        return map[std::forward<KeyType>(key)];
    }

    template <typename KeyType, detail::enable_if_t<detail::is_basic_node<KeyType>::value, int> = 0>
    const basic_node& at(KeyType&& key) const {
        const basic_node& act_node = resolve_reference();
        if FK_YAML_UNLIKELY (act_node.is_scalar_impl()) {
            throw fkyaml::type_error("at() is unavailable for a scalar node.", get_type());
        }

        if (act_node.is_sequence_impl()) {
            if FK_YAML_UNLIKELY (!key.is_integer()) {
                throw fkyaml::type_error("An argument of at() for sequence nodes must be an integer.", get_type());
            }

            FK_YAML_ASSERT(act_node.m_value.p_seq != nullptr);
            const sequence_type& seq = *act_node.m_value.p_seq;
            int index = std::forward<KeyType>(key).template get_value<int>();
            int size = static_cast<int>(seq.size());
            if FK_YAML_UNLIKELY (index >= size) {
                throw fkyaml::out_of_range(index);
            }
            return seq[index];
        }

        FK_YAML_ASSERT(act_node.m_value.p_map != nullptr);
        const mapping_type& map = *act_node.m_value.p_map;
        bool is_found = map.find(key) != map.end();
        if FK_YAML_UNLIKELY (!is_found) {
            throw fkyaml::out_of_range(serialize(key).c_str());
        }
        return map.at(std::forward<KeyType>(key));
    }

    yaml_version_type get_yaml_version_type() const noexcept {
        return mp_meta->is_version_specified ? mp_meta->version : yaml_version_type::VERSION_1_2;
    }

    void set_yaml_version_type(const yaml_version_type version) noexcept {
        mp_meta->version = version;
        mp_meta->is_version_specified = true;
    }

    FK_YAML_DEPRECATED("Since 0.3.12; Use get_yaml_version_type()")
    yaml_version_t get_yaml_version() const noexcept {
        yaml_version_type tmp_type = get_yaml_version_type();
        return detail::convert_from_yaml_version_type(tmp_type);
    }

    FK_YAML_DEPRECATED("Since 0.3.12; Use set_yaml_version_type(const yaml_version_type)")
    void set_yaml_version(const yaml_version_t version) noexcept {
        set_yaml_version_type(detail::convert_to_yaml_version_type(version));
    }

    bool has_anchor_name() const noexcept {
        return (m_attrs & detail::node_attr_mask::anchoring) && !m_prop.anchor.empty();
    }

    const std::string& get_anchor_name() const {
        if FK_YAML_UNLIKELY (!has_anchor_name()) {
            throw fkyaml::exception("No anchor name has been set.");
        }
        return m_prop.anchor;
    }

    void add_anchor_name(const std::string& anchor_name) {
        if (is_anchor()) {
            m_attrs &= ~detail::node_attr_mask::anchoring;
            auto itr = mp_meta->anchor_table.equal_range(m_prop.anchor).first;
            std::advance(itr, detail::node_attr_bits::get_anchor_offset(m_attrs));
            mp_meta.reset();
            itr->second.swap(*this);
            mp_meta->anchor_table.erase(itr);
        }

        auto p_meta = mp_meta;

        basic_node node;
        node.swap(*this);
        p_meta->anchor_table.emplace(anchor_name, std::move(node));

        m_attrs &= ~detail::node_attr_mask::anchoring;
        m_attrs |= detail::node_attr_bits::anchor_bit;
        mp_meta = p_meta;
        const auto offset = static_cast<uint32_t>(mp_meta->anchor_table.count(anchor_name) - 1);
        detail::node_attr_bits::set_anchor_offset(offset, m_attrs);
        m_prop.anchor = anchor_name;
    }

    void add_anchor_name(std::string&& anchor_name) {
        if (is_anchor()) {
            m_attrs &= ~detail::node_attr_mask::anchoring;
            auto itr = mp_meta->anchor_table.equal_range(m_prop.anchor).first;
            std::advance(itr, detail::node_attr_bits::get_anchor_offset(m_attrs));
            mp_meta.reset();
            itr->second.swap(*this);
            mp_meta->anchor_table.erase(itr);
        }

        auto p_meta = mp_meta;

        basic_node node;
        node.swap(*this);
        p_meta->anchor_table.emplace(anchor_name, std::move(node));

        m_attrs &= ~detail::node_attr_mask::anchoring;
        m_attrs |= detail::node_attr_bits::anchor_bit;
        mp_meta = p_meta;
        auto offset = static_cast<uint32_t>(mp_meta->anchor_table.count(anchor_name) - 1);
        detail::node_attr_bits::set_anchor_offset(offset, m_attrs);
        m_prop.anchor = std::move(anchor_name);
    }

    bool has_tag_name() const noexcept {
        return !m_prop.tag.empty();
    }

    const std::string& get_tag_name() const {
        if FK_YAML_UNLIKELY (!has_tag_name()) {
            throw fkyaml::exception("No tag name has been set.");
        }
        return m_prop.tag;
    }

    void add_tag_name(const std::string& tag_name) {
        m_prop.tag = tag_name;
    }

    void add_tag_name(std::string&& tag_name) {
        m_prop.tag = std::move(tag_name);
    }

    template <
        typename T, typename ValueType = detail::remove_cv_t<T>,
        detail::enable_if_t<
            detail::conjunction<std::is_default_constructible<ValueType>, detail::negation<std::is_pointer<T>>>::value,
            int> = 0>
    T get_value() const noexcept(
        noexcept(std::declval<const basic_node&>().template get_value_impl<ValueType>(std::declval<ValueType&>()))) {
        static_assert(
            !std::is_reference<T>::value,
            "get_value() cannot be called with reference types. "
            "You might want to call one of as_seq(), as_map(), as_bool(), as_int(), as_float() or as_str().");
        static_assert(
            !std::is_array<T>::value,
            "get_value() cannot be called with C-style array types. You might want to call get_value_inplace().");

        auto ret = ValueType();
        resolve_reference().get_value_impl(ret);
        return ret;
    }

    template <typename T>
    void get_value_inplace(T& value_ref) const
        noexcept(noexcept(std::declval<const basic_node&>().template get_value_impl<T>(std::declval<T&>()))) {
        resolve_reference().get_value_impl(value_ref);
    }

    template <
        typename T, typename U,
        detail::enable_if_t<
            detail::conjunction<
                std::is_constructible<T, U>, std::is_default_constructible<T>,
                detail::negation<std::is_pointer<T>>>::value,
            int> = 0>
    T get_value_or(U&& default_value) const noexcept {
        static_assert(
            !std::is_reference<T>::value,
            "get_value_or() cannot be called with reference types. "
            "You might want to call one of as_seq(), as_map(), as_bool(), as_int(), as_float() or as_str().");
        static_assert(
            !std::is_array<T>::value,
            "get_value_or() cannot be called with C-style array types. You might want to call get_value_inplace().");

        try {
            return get_value<T>();
        }
        catch (const std::exception&) {
            return std::forward<U>(default_value);
        }
    }

    template <typename ReferenceType, detail::enable_if_t<std::is_reference<ReferenceType>::value, int> = 0>
    FK_YAML_DEPRECATED("Since 0.4.3; Use one of as_seq(), as_map(), as_bool(), as_int(), as_float() or as_str()")
    ReferenceType get_value_ref() {
        return get_value_ref_impl(static_cast<detail::add_pointer_t<ReferenceType>>(nullptr));
    }

    template <
        typename ReferenceType,
        detail::enable_if_t<
            detail::conjunction<
                std::is_reference<ReferenceType>, std::is_const<detail::remove_reference_t<ReferenceType>>>::value,
            int> = 0>
    FK_YAML_DEPRECATED("Since 0.4.3; Use one of as_seq(), as_map(), as_bool(), as_int(), as_float() or as_str()")
    ReferenceType get_value_ref() const {
        return get_value_ref_impl(static_cast<detail::add_pointer_t<ReferenceType>>(nullptr));
    }

    sequence_type& as_seq() {
        basic_node& act_node = resolve_reference();
        if FK_YAML_LIKELY (act_node.is_sequence_impl()) {
            return *act_node.m_value.p_seq;
        }
        throw fkyaml::type_error("The node value is not a sequence.", get_type());
    }

    const sequence_type& as_seq() const {
        const basic_node& act_node = resolve_reference();
        if FK_YAML_LIKELY (act_node.is_sequence_impl()) {
            return *act_node.m_value.p_seq;
        }
        throw fkyaml::type_error("The node value is not a sequence.", get_type());
    }

    mapping_type& as_map() {
        basic_node& act_node = resolve_reference();
        if FK_YAML_LIKELY (act_node.is_mapping_impl()) {
            return *act_node.m_value.p_map;
        }
        throw fkyaml::type_error("The node value is not a mapping.", get_type());
    }

    const mapping_type& as_map() const {
        const basic_node& act_node = resolve_reference();
        if FK_YAML_LIKELY (act_node.is_mapping_impl()) {
            return *act_node.m_value.p_map;
        }
        throw fkyaml::type_error("The node value is not a mapping.", get_type());
    }

    boolean_type& as_bool() {
        basic_node& act_node = resolve_reference();
        if FK_YAML_LIKELY (act_node.is_boolean_impl()) {
            return act_node.m_value.boolean;
        }
        throw fkyaml::type_error("The node value is not a boolean.", get_type());
    }

    const boolean_type& as_bool() const {
        const basic_node& act_node = resolve_reference();
        if FK_YAML_LIKELY (act_node.is_boolean_impl()) {
            return act_node.m_value.boolean;
        }
        throw fkyaml::type_error("The node value is not a boolean.", get_type());
    }

    integer_type& as_int() {
        basic_node& act_node = resolve_reference();
        if FK_YAML_LIKELY (act_node.is_integer_impl()) {
            return act_node.m_value.integer;
        }
        throw fkyaml::type_error("The node value is not an integer.", get_type());
    }

    const integer_type& as_int() const {
        const basic_node& act_node = resolve_reference();
        if FK_YAML_LIKELY (act_node.is_integer_impl()) {
            return act_node.m_value.integer;
        }
        throw fkyaml::type_error("The node value is not an integer.", get_type());
    }

    float_number_type& as_float() {
        basic_node& act_node = resolve_reference();
        if FK_YAML_LIKELY (act_node.is_float_number_impl()) {
            return act_node.m_value.float_val;
        }
        throw fkyaml::type_error("The node value is not a float.", get_type());
    }

    const float_number_type& as_float() const {
        const basic_node& act_node = resolve_reference();
        if FK_YAML_LIKELY (act_node.is_float_number_impl()) {
            return act_node.m_value.float_val;
        }
        throw fkyaml::type_error("The node value is not a float.", get_type());
    }

    string_type& as_str() {
        basic_node& act_node = resolve_reference();
        if FK_YAML_LIKELY (act_node.is_string_impl()) {
            return *act_node.m_value.p_str;
        }
        throw fkyaml::type_error("The node value is not a string.", get_type());
    }

    const string_type& as_str() const {
        const basic_node& act_node = resolve_reference();
        if FK_YAML_LIKELY (act_node.is_string_impl()) {
            return *act_node.m_value.p_str;
        }
        throw fkyaml::type_error("The node value is not a string.", get_type());
    }

    void swap(basic_node& rhs) noexcept {
        using std::swap;
        swap(m_attrs, rhs.m_attrs);
        swap(mp_meta, rhs.mp_meta);

        node_value tmp {};
        std::memcpy(&tmp, &m_value, sizeof(node_value));
        std::memcpy(&m_value, &rhs.m_value, sizeof(node_value));
        std::memcpy(&rhs.m_value, &tmp, sizeof(node_value));

        swap(m_prop.tag, rhs.m_prop.tag);
        swap(m_prop.anchor, rhs.m_prop.anchor);
    }

    iterator begin() {
        basic_node& act_node = resolve_reference();
        switch (act_node.m_attrs & detail::node_attr_mask::value) {
        case detail::node_attr_bits::seq_bit:
            FK_YAML_ASSERT(act_node.m_value.p_seq != nullptr);
            return {act_node.m_value.p_seq->begin()};
        case detail::node_attr_bits::map_bit:
            FK_YAML_ASSERT(act_node.m_value.p_map != nullptr);
            return {act_node.m_value.p_map->begin()};
        default:
            throw fkyaml::type_error("The target node is neither of sequence nor mapping types.", get_type());
        }
    }

    const_iterator begin() const {
        const basic_node& act_node = resolve_reference();
        switch (act_node.m_attrs & detail::node_attr_mask::value) {
        case detail::node_attr_bits::seq_bit:
            FK_YAML_ASSERT(act_node.m_value.p_seq != nullptr);
            return {act_node.m_value.p_seq->begin()};
        case detail::node_attr_bits::map_bit:
            FK_YAML_ASSERT(act_node.m_value.p_map != nullptr);
            return {act_node.m_value.p_map->begin()};
        default:
            throw fkyaml::type_error("The target node is neither of sequence nor mapping types.", get_type());
        }
    }

    const_iterator cbegin() const {
        return begin();
    }

    iterator end() {
        basic_node& act_node = resolve_reference();
        switch (act_node.m_attrs & detail::node_attr_mask::value) {
        case detail::node_attr_bits::seq_bit:
            FK_YAML_ASSERT(act_node.m_value.p_seq != nullptr);
            return {act_node.m_value.p_seq->end()};
        case detail::node_attr_bits::map_bit:
            FK_YAML_ASSERT(act_node.m_value.p_map != nullptr);
            return {act_node.m_value.p_map->end()};
        default:
            throw fkyaml::type_error("The target node is neither of sequence nor mapping types.", get_type());
        }
    }

    const_iterator end() const {
        const basic_node& act_node = resolve_reference();
        switch (act_node.m_attrs & detail::node_attr_mask::value) {
        case detail::node_attr_bits::seq_bit:
            FK_YAML_ASSERT(act_node.m_value.p_seq != nullptr);
            return {act_node.m_value.p_seq->end()};
        case detail::node_attr_bits::map_bit:
            FK_YAML_ASSERT(act_node.m_value.p_map != nullptr);
            return {act_node.m_value.p_map->end()};
        default:
            throw fkyaml::type_error("The target node is neither of sequence nor mapping types.", get_type());
        }
    }

    const_iterator cend() const {
        return end();
    }

    reverse_iterator rbegin() {
        return {end()};
    }

    const_reverse_iterator rbegin() const {
        return {end()};
    }

    const_reverse_iterator crbegin() const {
        return rbegin();
    }

    reverse_iterator rend() {
        return {begin()};
    }

    const_reverse_iterator rend() const {
        return {begin()};
    }

    const_reverse_iterator crend() const {
        return rend();
    }

    map_range map_items() {
        if FK_YAML_UNLIKELY (!is_mapping()) {
            throw type_error("map_items() cannot be called on a non-mapping node.", get_type());
        }
        return {*this};
    }

    const_map_range map_items() const {
        if FK_YAML_UNLIKELY (!is_mapping()) {
            throw type_error("map_items() cannot be called on a non-mapping node.", get_type());
        }
        return {*this};
    }

private:
    basic_node& resolve_reference() {
        if FK_YAML_UNLIKELY (has_anchor_name()) {
            auto itr = mp_meta->anchor_table.equal_range(m_prop.anchor).first;
            std::advance(itr, detail::node_attr_bits::get_anchor_offset(m_attrs));
            return itr->second;
        }
        return *this;
    }

    const basic_node& resolve_reference() const {
        if FK_YAML_UNLIKELY (has_anchor_name()) {
            auto itr = mp_meta->anchor_table.equal_range(m_prop.anchor).first;
            std::advance(itr, detail::node_attr_bits::get_anchor_offset(m_attrs));
            return itr->second;
        }
        return *this;
    }

    bool is_sequence_impl() const noexcept {
        return m_attrs & detail::node_attr_bits::seq_bit;
    }

    bool is_mapping_impl() const noexcept {
        return m_attrs & detail::node_attr_bits::map_bit;
    }

    bool is_null_impl() const noexcept {
        return m_attrs & detail::node_attr_bits::null_bit;
    }

    bool is_boolean_impl() const noexcept {
        return m_attrs & detail::node_attr_bits::bool_bit;
    }

    bool is_integer_impl() const noexcept {
        return m_attrs & detail::node_attr_bits::int_bit;
    }

    bool is_float_number_impl() const noexcept {
        return m_attrs & detail::node_attr_bits::float_bit;
    }

    bool is_string_impl() const noexcept {
        return m_attrs & detail::node_attr_bits::string_bit;
    }

    bool is_scalar_impl() const noexcept {
        return m_attrs & detail::node_attr_bits::scalar_bits;
    }

    template <
        typename ValueType, detail::enable_if_t<detail::negation<detail::is_basic_node<ValueType>>::value, int> = 0>
    void get_value_impl(ValueType& v) const
        noexcept(noexcept(ConverterType<ValueType, void>::from_node(std::declval<const basic_node&>(), v))) {
        ConverterType<ValueType, void>::from_node(*this, v);
    }

    template <typename ValueType, detail::enable_if_t<detail::is_basic_node<ValueType>::value, int> = 0>
    void get_value_impl(ValueType& v) const {
        v = *this;
    }

    sequence_type& get_value_ref_impl(sequence_type*) {
        return as_seq();
    }

    const sequence_type& get_value_ref_impl(const sequence_type*) const {
        return as_seq();
    }

    mapping_type& get_value_ref_impl(mapping_type*) {
        return as_map();
    }

    const mapping_type& get_value_ref_impl(const mapping_type*) const {
        return as_map();
    }

    boolean_type& get_value_ref_impl(boolean_type*) {
        return as_bool();
    }

    const boolean_type& get_value_ref_impl(const boolean_type*) const {
        return as_bool();
    }

    integer_type& get_value_ref_impl(integer_type*) {
        return as_int();
    }

    const integer_type& get_value_ref_impl(const integer_type*) const {
        return as_int();
    }

    float_number_type& get_value_ref_impl(float_number_type*) {
        return as_float();
    }

    const float_number_type& get_value_ref_impl(const float_number_type*) const {
        return as_float();
    }

    string_type& get_value_ref_impl(string_type*) {
        return as_str();
    }

    const string_type& get_value_ref_impl(const string_type*) const {
        return as_str();
    }

    detail::node_attr_t m_attrs {detail::node_attr_bits::default_bits};
    mutable std::shared_ptr<detail::document_metainfo<basic_node>> mp_meta {
        std::shared_ptr<detail::document_metainfo<basic_node>>(new detail::document_metainfo<basic_node>())};
    node_value m_value {};
    detail::node_property m_prop {};
};

template <
    template <typename, typename...> class SequenceType, template <typename, typename, typename...> class MappingType,
    typename BooleanType, typename IntegerType, typename FloatNumberType, typename StringType,
    template <typename, typename = void> class ConverterType>
inline void swap(
    basic_node<SequenceType, MappingType, BooleanType, IntegerType, FloatNumberType, StringType, ConverterType>& lhs,
    basic_node<SequenceType, MappingType, BooleanType, IntegerType, FloatNumberType, StringType, ConverterType>&
        rhs) noexcept(noexcept(lhs.swap(rhs))) {
    lhs.swap(rhs);
}

template <
    template <typename, typename...> class SequenceType, template <typename, typename, typename...> class MappingType,
    typename BooleanType, typename IntegerType, typename FloatNumberType, typename StringType,
    template <typename, typename = void> class ConverterType>
inline std::ostream& operator<<(
    std::ostream& os,
    const basic_node<SequenceType, MappingType, BooleanType, IntegerType, FloatNumberType, StringType, ConverterType>&
        n) {
    os << basic_node<SequenceType, MappingType, BooleanType, IntegerType, FloatNumberType, StringType, ConverterType>::
            serialize(n);
    return os;
}

template <
    template <typename, typename...> class SequenceType, template <typename, typename, typename...> class MappingType,
    typename BooleanType, typename IntegerType, typename FloatNumberType, typename StringType,
    template <typename, typename = void> class ConverterType>
inline std::istream& operator>>(
    std::istream& is,
    basic_node<SequenceType, MappingType, BooleanType, IntegerType, FloatNumberType, StringType, ConverterType>& n) {
    n = basic_node<SequenceType, MappingType, BooleanType, IntegerType, FloatNumberType, StringType, ConverterType>::
        deserialize(is);
    return is;
}

inline namespace literals {
inline namespace yaml_literals {

#if defined(__clang__)
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wdeprecated"
#endif

#if defined(__GNUC__) && (__GNUC__ > 6)
#define FK_YAML_QUOTE_OPERATOR operator""_yaml
#else
#define FK_YAML_QUOTE_OPERATOR operator"" _yaml
#endif

inline fkyaml::node FK_YAML_QUOTE_OPERATOR(const char* s, std::size_t n) {
    return fkyaml::node::deserialize(s, s + n);
}

inline fkyaml::node FK_YAML_QUOTE_OPERATOR(const char16_t* s, std::size_t n) {
    return fkyaml::node::deserialize(s, s + n);
}

inline fkyaml::node FK_YAML_QUOTE_OPERATOR(const char32_t* s, std::size_t n) {
    return fkyaml::node::deserialize(s, s + n);
}

#if FK_YAML_HAS_CHAR8_T
inline fkyaml::node FK_YAML_QUOTE_OPERATOR(const char8_t* s, std::size_t n) {
    return fkyaml::node::deserialize(s, s + n);
}

#if defined(__clang__)
#pragma clang diagnostic pop
#endif

#endif

}
}

FK_YAML_NAMESPACE_END

namespace std {

template <
    template <typename, typename...> class SequenceType, template <typename, typename, typename...> class MappingType,
    typename BooleanType, typename IntegerType, typename FloatNumberType, typename StringType,
    template <typename, typename = void> class ConverterType>
struct hash<fkyaml::basic_node<
    SequenceType, MappingType, BooleanType, IntegerType, FloatNumberType, StringType, ConverterType>> {
    using node_t = fkyaml::basic_node<
        SequenceType, MappingType, BooleanType, IntegerType, FloatNumberType, StringType, ConverterType>;

    std::size_t operator()(const node_t& n) const {
        using boolean_type = typename node_t::boolean_type;
        using integer_type = typename node_t::integer_type;
        using float_number_type = typename node_t::float_number_type;
        using string_type = typename node_t::string_type;

        const auto type = n.get_type();

        std::size_t seed = 0;
        hash_combine(seed, std::hash<uint8_t>()(static_cast<uint8_t>(type)));

        switch (type) {
        case fkyaml::node_type::SEQUENCE:
            hash_combine(seed, n.size());
            for (const auto& elem : n) {
                hash_combine(seed, operator()(elem));
            }
            return seed;

        case fkyaml::node_type::MAPPING:
            hash_combine(seed, n.size());
            for (auto itr = n.begin(), end_itr = n.end(); itr != end_itr; ++itr) {
                hash_combine(seed, operator()(itr.key()));
                hash_combine(seed, operator()(itr.value()));
            }
            return seed;

        case fkyaml::node_type::NULL_OBJECT:
            hash_combine(seed, 0);
            return seed;
        case fkyaml::node_type::BOOLEAN:
            hash_combine(seed, std::hash<boolean_type>()(n.template get_value<boolean_type>()));
            return seed;
        case fkyaml::node_type::INTEGER:
            hash_combine(seed, std::hash<integer_type>()(n.template get_value<integer_type>()));
            return seed;
        case fkyaml::node_type::FLOAT:
            hash_combine(seed, std::hash<float_number_type>()(n.template get_value<float_number_type>()));
            return seed;
        case fkyaml::node_type::STRING:
            hash_combine(seed, std::hash<string_type>()(n.template get_value<string_type>()));
            return seed;
        default:
            fkyaml::detail::unreachable();
        }
    }

private:
    FK_YAML_NO_SANITIZE("unsigned-shift-base", "unsigned-integer-overflow")
    static void hash_combine(std::size_t& seed, std::size_t v) {
        seed ^= v + 0x9e3779b9 + (seed << 6u) + (seed >> 2u);
    }
};

}
