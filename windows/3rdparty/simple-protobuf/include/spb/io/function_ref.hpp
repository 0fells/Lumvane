#pragma once
#pragma once

#include <cstdint>
#include <type_traits>
#include <utility>

namespace spb::detail
{

template < typename Fn >
class function_ref;

template < typename Ret, typename... Params >
class function_ref< Ret( Params... ) >
{
    Ret ( *callback )( intptr_t callable, Params... params ) = nullptr;
    intptr_t callable;

    template < typename Callable >
    static Ret callback_fn( intptr_t callable, Params... params )
    {
        return ( *reinterpret_cast< Callable * >( callable ) )(
            std::forward< Params >( params )... );
    }

public:
    function_ref( ) = default;
    function_ref( std::nullptr_t )
    {
    }

    template < typename Callable >
    function_ref( Callable && callable,
                  std::enable_if_t< !std::is_same< std::remove_cvref_t< Callable >,
                                                   function_ref >::value > * = nullptr,
                  std::enable_if_t< std::is_void< Ret >::value ||
                                    std::is_convertible< decltype( std::declval< Callable >( )(
                                                             std::declval< Params >( )... ) ),
                                                         Ret >::value > * = nullptr )
        : callback( callback_fn< std::remove_reference_t< Callable > > )
        , callable( reinterpret_cast< intptr_t >( &callable ) )
    {
    }

    Ret operator( )( Params... params ) const
    {
        return callback( callable, std::forward< Params >( params )... );
    }

    explicit operator bool( ) const
    {
        return callback;
    }
};
}
